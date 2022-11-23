package de.intension.mapper.oidc;

import static de.intension.api.UserInfoAttribute.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.intension.api.UserInfoAttribute;
import de.intension.api.json.UserInfo;
import de.intension.mapper.user.UserInfoHelper;

public class UserInfoProviderMapper
        extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper
{

    public static final String                        PROVIDER_ID              = "user-info-provider-mapper";
    public static final String                        USER_INFO_ATTRIBUTE_NAME = "userInfo";
    protected static final Logger                     logger                   = Logger.getLogger(UserInfoProviderMapper.class);
    private static final String                       CATEGORY                 = "User Info Mapper";
    private static final List<ProviderConfigProperty> configProperties         = new ArrayList<>();

    private static final UserInfoHelper               userInfoHelper           = new UserInfoHelper();

    static {
        addConfigEntry(HEIMATORGANISATION_NAME);
        addConfigEntry(HEIMATORGANISATION_BUNDESLAND);
        addConfigEntry(PERSON_FAMILIENNAME);
        addConfigEntry(PERSON_VORNAME);
        addConfigEntry(PERSON_AKRONYM);
        addConfigEntry(PERSON_GEBURTSDATUM);
        addConfigEntry(PERSON_GESCHLECHT);
        addConfigEntry(PERSON_LOKALISIERUNG);
        addConfigEntry(PERSON_VERTRAUENSSTUFE);
        addConfigEntry(PERSON_KONTEXT_ORG_VIDIS_ID);
        addConfigEntry(PERSON_KONTEXT_ORG_KENNUNG);
        addConfigEntry(PERSON_KONTEXT_ORG_NAME);
        addConfigEntry(PERSON_KONTEXT_ORG_TYP);
        addConfigEntry(PERSON_KONTEXT_ROLLE);
        addConfigEntry(PERSON_KONTEXT_STATUS);
        OIDCAttributeMapperHelper.addAttributeConfig(configProperties, UserInfoProviderMapper.class);
        setDefaultTokeClaimNameValue();
        setDefaultTokeClaimType();
    }

    /**
     * Set default for field "claim.name".
     */
    private static void setDefaultTokeClaimNameValue()
    {
        Optional<ProviderConfigProperty> config = configProperties.stream().filter(p -> p.getName().equals(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME))
            .findFirst();
        if (!config.isEmpty()) {
            config.get().setDefaultValue(USER_INFO_ATTRIBUTE_NAME);
        }
    }

    /**
     * Set default for field "jsonType.label".
     */
    private static void setDefaultTokeClaimType()
    {
        Optional<ProviderConfigProperty> config = configProperties.stream().filter(p -> p.getName().equals(OIDCAttributeMapperHelper.JSON_TYPE))
            .findFirst();
        if (!config.isEmpty()) {
            config.get().setDefaultValue("JSON");
        }
    }

    /**
     * Add custom configuration entries.
     */
    private static void addConfigEntry(UserInfoAttribute attribute)
    {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName(attribute.getAttributeName());
        property.setLabel(attribute.getLabel());
        property.setDefaultValue(attribute.isEnabled().toString());
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession, KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx)
    {
        UserModel user = userSession.getUser();
        UserInfo userInfo = userInfoHelper.getUserInfoFromKeycloakUser(token, mappingModel, user);
        if (!userInfo.isEmpty()) {
            try {
                OIDCAttributeMapperHelper.mapClaim(token, mappingModel, UserInfo.getJsonRepresentation(userInfo));
            } catch (JsonProcessingException e) {
                logger.error("Error while creating userInfo claim", e);
            }
        }
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties()
    {
        return configProperties;
    }

    @Override
    public String getId()
    {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType()
    {
        return getDisplayCategory();
    }

    @Override
    public String getDisplayCategory()
    {
        return CATEGORY;
    }

    @Override
    public String getHelpText()
    {
        return "Adds userInfo field to the Token";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }
}
