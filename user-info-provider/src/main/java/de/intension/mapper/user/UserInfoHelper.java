package de.intension.mapper.user;

import static de.intension.api.UserInfoAttribute.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.IDToken;
import org.keycloak.utils.StringUtil;

import com.google.common.hash.Hashing;

import de.intension.api.UserInfoAttribute;
import de.intension.api.enumerations.*;
import de.intension.api.json.*;

public class UserInfoHelper
{

    protected static final Logger logger                = Logger.getLogger(UserInfoHelper.class);
    private static final String   LOG_UNSUPPORTED_VALUE = "Unsupported value %s for %s";

    /**
     * Create userInfo attribute from users attributes.
     */
    public UserInfo getUserInfoFromKeycloakUser(IDToken token, ProtocolMapperModel mappingModel, UserModel user)
    {
        UserInfo userInfo = new UserInfo();
        userInfo.setPid(token.getSubject());
        String heimatOrgName = addHeimatOrganisation(userInfo, mappingModel, user);
        addPerson(userInfo, mappingModel, user);
        addDefaultPersonKontext(userInfo, mappingModel, user, heimatOrgName);
        addPersonenKontextArray(userInfo, mappingModel, user, heimatOrgName);
        return userInfo;
    }

    /**
     * Add {@link Personenkontext} json structure to userInfo claim.
     */
    private void addDefaultPersonKontext(UserInfo userInfo, ProtocolMapperModel mappingModel, UserModel user, String heimatOrgName)
    {
        Personenkontext kontext = new Personenkontext();
        Rolle rolle = getRolle(user, -1);
        kontext.setKtid(getKit(user, heimatOrgName, rolle, -1));
        Organisation organisation = getOrganisation(mappingModel, user, heimatOrgName, rolle);
        if (isActive(PERSON_KONTEXT_ROLLE, mappingModel)) {
            kontext.setRolle(rolle);
        }
        if (isActive(PERSON_KONTEXT_STATUS, mappingModel)) {
            String status = resolveSingleAttributeValue(user, PERSON_KONTEXT_STATUS);
            if (status != null) {
                try {
                    kontext.setPersonenstatus(PersonenStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, status, PERSON_KONTEXT_STATUS.getAttributeName());
                }
            }
        }
        if (!organisation.isEmpty()) {
            addVidisSchulIdentifikator(mappingModel, user, userInfo, organisation, -1);
            kontext.setOrganisation(organisation);
        }
        if (!kontext.isEmpty()) {
            userInfo.getPersonenKontexte().add(kontext);
        }
    }

    /**
     * Add {@link Organisation} json structure to userInfo claim.
     */
    private Organisation getOrganisation(ProtocolMapperModel mappingModel, UserModel user, String heimatOrgName, Rolle rolle)
    {
        Organisation organisation = new Organisation();
        String kennung = resolveSingleAttributeValue(user, PERSON_KONTEXT_ORG_KENNUNG);
        organisation.setOrgid(getOrgId(user, heimatOrgName, rolle, kennung, -1));
        if (isActive(PERSON_KONTEXT_ORG_KENNUNG, mappingModel)) {
            organisation.setKennung(kennung);
        }
        if (isActive(PERSON_KONTEXT_ORG_NAME, mappingModel)) {
            organisation.setName(resolveSingleAttributeValue(user, PERSON_KONTEXT_ORG_NAME));
        }
        if (isActive(PERSON_KONTEXT_ORG_TYP, mappingModel)) {
            String orgTyp = resolveSingleAttributeValue(user, PERSON_KONTEXT_ORG_TYP);
            if (orgTyp != null) {
                try {
                    organisation.setTyp(OrganisationsTyp.valueOf(orgTyp));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, orgTyp, PERSON_KONTEXT_ORG_TYP.getAttributeName());
                }
            }
        }
        return organisation;
    }

    /**
     * Add @{@link HeimatOrganisation} json structure to userInfo claim.
     */
    private String addHeimatOrganisation(UserInfo userInfo, ProtocolMapperModel mappingModel, UserModel user)
    {
        HeimatOrganisation heimatOrganisation = new HeimatOrganisation();
        String orgName = resolveSingleAttributeValue(user, HEIMATORGANISATION_NAME);
        if (isActive(HEIMATORGANISATION_NAME, mappingModel)) {
            heimatOrganisation.setName(orgName);
        }
        if (isActive(HEIMATORGANISATION_BUNDESLAND, mappingModel)) {
            heimatOrganisation.setBundesland(resolveSingleAttributeValue(user, HEIMATORGANISATION_BUNDESLAND));
        }
        if (!heimatOrganisation.isEmpty()) {
            userInfo.setHeimatOrganisation(heimatOrganisation);
        }
        return orgName;
    }

    /**
     * Add {@link Person} json structure to userInfo claim.
     */
    private void addPerson(UserInfo userInfo, ProtocolMapperModel mappingModel, UserModel user)
    {
        Person person = new Person();
        addPersonName(person, mappingModel, user);
        addGeschlecht(person, mappingModel, user);
        if (isActive(PERSON_GEBURTSDATUM, mappingModel)) {
            String geburtsdatum = resolveSingleAttributeValue(user, PERSON_GEBURTSDATUM);
            if (StringUtil.isNotBlank(geburtsdatum)) {
                Geburt geburt = new Geburt(geburtsdatum);
                person.setGeburt(geburt);
            }
        }
        if (isActive(PERSON_LOKALISIERUNG, mappingModel)) {
            person.setLokalisierung(resolveSingleAttributeValue(user, PERSON_LOKALISIERUNG));
        }
        if (isActive(PERSON_VERTRAUENSSTUFE, mappingModel)) {
            String vertrauensstufe = resolveSingleAttributeValue(user, PERSON_VERTRAUENSSTUFE);
            if (vertrauensstufe != null) {
                try {
                    person.setVertrauensstufe(Vertrauensstufe.valueOf(vertrauensstufe));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, vertrauensstufe, PERSON_VERTRAUENSSTUFE.getAttributeName());
                }
            }
        }
        if (!person.isEmpty()) {
            userInfo.setPerson(person);
        }
    }

    /**
     * Add {@link PersonName} json structure to userInfo claim.
     */
    private void addPersonName(Person person, ProtocolMapperModel mappingModel, UserModel user)
    {
        PersonName personName = new PersonName();
        String familienName = resolveSingleAttributeValue(user, PERSON_FAMILIENNAME);
        String vorname = resolveSingleAttributeValue(user, PERSON_VORNAME);
        if (isActive(PERSON_FAMILIENNAME, mappingModel)) {
            personName.setFamilienname(familienName);
        }
        if (isActive(PERSON_VORNAME, mappingModel)) {
            personName.setVorname(vorname);
        }
        if (isActive(PERSON_AKRONYM, mappingModel)) {
            String akronym = resolveSingleAttributeValue(user, PERSON_AKRONYM);
            if ((akronym == null || akronym.isBlank()) && vorname != null && vorname.length() >= 2 && familienName != null && familienName.length() >= 2) {
                akronym = vorname.substring(0, 2).concat(familienName.substring(0, 2));
            }
            if (akronym != null) {
                personName.setAkronym(akronym.toLowerCase());
            }
        }
        if (!personName.isEmpty()) {
            person.setPerson(personName);
        }
    }

    /**
     * Add {@link Geschlecht} json structure to userInfo claim.
     */
    private void addGeschlecht(Person person, ProtocolMapperModel mappingModel, UserModel user)
    {
        if (isActive(PERSON_GESCHLECHT, mappingModel)) {
            String geschlecht = resolveSingleAttributeValue(user, PERSON_GESCHLECHT);
            if (geschlecht != null) {
                try {
                    person.setGeschlecht(Geschlecht.valueOf(geschlecht));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, geschlecht, PERSON_GESCHLECHT.getAttributeName());
                }
            }
        }
    }

    /**
     * Add personenkontext arrays.
     */
    private void addPersonenKontextArray(UserInfo userInfo, ProtocolMapperModel mappingModel, UserModel user, String heimatOrgName)
    {
        Set<Integer> indizes = getPersonenKontexteIndizes(user);
        for (Integer i : indizes) {
            Personenkontext kontext = getKontextArr(userInfo, mappingModel, user, heimatOrgName, i);
            if (!kontext.isEmpty()) {
                userInfo.getPersonenKontexte().add(kontext);
            }
        }
    }

    /**
     * Get single Kontext from array structure.
     */
    private Personenkontext getKontextArr(UserInfo userInfo, ProtocolMapperModel mappingModel, UserModel user, String heimatOrgName, Integer i)
    {
        Rolle rolle = getRolle(user, i);
        Personenkontext kontext = new Personenkontext();
        kontext.setKtid(getKit(user, heimatOrgName, rolle, i));
        Organisation organisation = getOrganisationArray(mappingModel, user, heimatOrgName, rolle, i);
        if (isActive(PERSON_KONTEXT_ROLLE, mappingModel)) {
            kontext.setRolle(rolle);
        }
        if (isActive(PERSON_KONTEXT_STATUS, mappingModel)) {
            String status = resolveSingleAttributeValue(user, PERSON_KONTEXT_ARRAY_STATUS, i);
            if (status != null) {
                try {
                    kontext.setPersonenstatus(PersonenStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, status, PERSON_KONTEXT_STATUS.getAttributeName());
                }
            }
        }
        if (!organisation.isEmpty()) {
            addVidisSchulIdentifikator(mappingModel, user, userInfo, organisation, i);
            kontext.setOrganisation(organisation);
        }
        return kontext;
    }

    /**
     * Add {@link Organisation} json structure to userInfo claim.
     */
    private Organisation getOrganisationArray(ProtocolMapperModel mappingModel, UserModel user, String heimatOrgName, Rolle rolle, Integer i)
    {
        Organisation organisation = new Organisation();
        String kennung = resolveSingleAttributeValue(user, PERSON_KONTEXT_ARRAY_ORG_KENNUNG, i);
        organisation.setOrgid(getOrgId(user, heimatOrgName, rolle, kennung, i));
        if (isActive(PERSON_KONTEXT_ORG_KENNUNG, mappingModel)) {
            organisation.setKennung(kennung);
        }
        if (isActive(PERSON_KONTEXT_ORG_NAME, mappingModel)) {
            organisation.setName(resolveSingleAttributeValue(user, PERSON_KONTEXT_ARRAY_ORG_NAME, i));
        }
        if (isActive(PERSON_KONTEXT_ORG_TYP, mappingModel)) {
            String orgTyp = resolveSingleAttributeValue(user, PERSON_KONTEXT_ARRAY_ORG_TYP, i);
            if (orgTyp != null) {
                try {
                    organisation.setTyp(OrganisationsTyp.valueOf(orgTyp));
                } catch (IllegalArgumentException e) {
                    logger.errorf(LOG_UNSUPPORTED_VALUE, orgTyp, PERSON_KONTEXT_ORG_TYP.getAttributeName());
                }
            }
        }
        return organisation;
    }

    /**
     * Add vidis schulidentifikator to Organisation
     */
    private void addVidisSchulIdentifikator(ProtocolMapperModel mappingModel, UserModel user, UserInfo userInfo, Organisation org, Integer index)
    {
        UserInfoAttribute attribute = PERSON_KONTEXT_ORG_VIDIS_ID;
        if (index != -1) {
            attribute = PERSON_KONTEXT_ARRAY_ORG_VIDIS_ID;
        }
        if (isActive(PERSON_KONTEXT_ORG_VIDIS_ID, mappingModel)) {
            String vidisId = resolveSingleAttributeValue(user, attribute, index);
            if ((vidisId == null || vidisId.isEmpty()) && userInfo.getHeimatOrganisation() != null
                    && userInfo.getHeimatOrganisation().getName() != null &&
                    !userInfo.getHeimatOrganisation().getName().isEmpty() && org.getKennung() != null && !org.getKennung().isEmpty()) {
                org.setVidisSchulidentifikator(String.format("%s.%s", userInfo.getHeimatOrganisation().getName(), org.getKennung()).toLowerCase());
            }
            else if (vidisId != null) {
                org.setVidisSchulidentifikator(vidisId);
            }
        }
    }

    /**
     * Get all personenkontext indices.
     */
    private Set<Integer> getPersonenKontexteIndizes(UserModel user)
    {
        Set<Integer> indices = new HashSet<>();
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            Pattern pattern = Pattern.compile("^person\\.kontext\\[(\\d+)]\\..*");
            for (String attributeKey : attributes.keySet()) {
                Matcher matcher = pattern.matcher(attributeKey);
                if (matcher.matches()) {
                    String index = matcher.group(1);
                    indices.add(Integer.valueOf(index));
                }
            }
        }
        return indices;
    }

    /**
     * Resolve single user attribute value.
     */
    private String resolveSingleAttributeValue(UserModel user, UserInfoAttribute attribute)
    {
        return resolveSingleAttributeValue(user, attribute, -1);
    }

    /**
     * Resolve single user attribute value with array support (index >= 0).
     */
    private String resolveSingleAttributeValue(UserModel user, UserInfoAttribute attribute, int index)
    {
        Collection<String> values;
        if (index == -1) {
            values = KeycloakModelUtils.resolveAttribute(user, attribute.getAttributeName(), false);
        }
        else {
            values = KeycloakModelUtils.resolveAttribute(user, attribute.getAttributeName().replace("#", String.valueOf(index)), false);
        }
        if (!values.isEmpty()) {
            return values.stream().iterator().next();
        }
        else if (attribute.getDefaultValue() != null) {
            return attribute.getDefaultValue().toString();
        }
        return null;
    }

    /**
     * Get role from person context.
     */
    private Rolle getRolle(UserModel user, Integer index)
    {
        UserInfoAttribute attribute = PERSON_KONTEXT_ROLLE;
        if (index != -1) {
            attribute = PERSON_KONTEXT_ARRAY_ROLLE;
        }
        Rolle rolle = null;
        String sRolle = resolveSingleAttributeValue(user, attribute, index);
        if (sRolle != null) {
            try {
                rolle = Rolle.valueOf(sRolle);
            } catch (IllegalArgumentException e) {
                logger.errorf(LOG_UNSUPPORTED_VALUE, sRolle, attribute.getAttributeName());
            }
        }
        return rolle;
    }

    /**
     * Get generated context id hash.
     */
    private String getKit(UserModel user, String heimatOrgName, Rolle rolle, Integer index)
    {
        UserInfoAttribute attribute = PERSON_KONTEXT_ID;
        if (index != null) {
            attribute = PERSON_KONTEXT_ARRAY_ID;
        }
        String kontextId = resolveSingleAttributeValue(user, attribute, index);
        if ((kontextId == null || kontextId.isEmpty()) && rolle != null && heimatOrgName != null && !heimatOrgName.isEmpty()) {
            String builder = rolle.name() + heimatOrgName;
            kontextId = Hashing.sha256()
                .hashString(builder, StandardCharsets.UTF_8)
                .toString();
        }
        return kontextId;
    }

    /**
     * Get generated organisation id hash.
     */
    private String getOrgId(UserModel user, String heimatOrgName, Rolle rolle, String kennung, Integer index)
    {
        UserInfoAttribute attribute = PERSON_KONTEXT_ORG_ID;
        if (index != -1) {
            attribute = PERSON_KONTEXT_ARRAY_ORG_ID;
        }
        String orgId = resolveSingleAttributeValue(user, attribute, index);
        if ((orgId == null || orgId.isEmpty()) && rolle != null && heimatOrgName != null && !heimatOrgName.isEmpty() && kennung != null && !kennung.isEmpty()) {
            String builder = rolle.name() + heimatOrgName + kennung;
            orgId = Hashing.sha256()
                .hashString(builder, StandardCharsets.UTF_8)
                .toString();
        }
        return orgId;
    }

    /**
     * Is user attribute send to Service Provider by default.
     */
    private boolean isActive(UserInfoAttribute userInfoAttribute, ProtocolMapperModel mappingModel)
    {
        String value = mappingModel.getConfig().get(userInfoAttribute.getAttributeName());
        return Boolean.valueOf(value);
    }

}
