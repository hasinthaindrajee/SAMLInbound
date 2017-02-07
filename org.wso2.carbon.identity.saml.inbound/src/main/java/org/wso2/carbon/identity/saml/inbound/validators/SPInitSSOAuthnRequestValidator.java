/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.identity.saml.inbound.validators;

import org.apache.commons.lang.StringUtils;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.RequestAbstractType;
import org.opensaml.saml2.core.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.common.base.exception.IdentityException;
import org.wso2.carbon.identity.gateway.api.FrameworkHandlerResponse;
import org.wso2.carbon.identity.gateway.context.AuthenticationContext;
import org.wso2.carbon.identity.gateway.processor.handler.request.RequestHandlerException;
import org.wso2.carbon.identity.saml.inbound.SAMLSSOConstants;
import org.wso2.carbon.identity.saml.inbound.context.SAMLMessageContext;
import org.wso2.carbon.identity.saml.inbound.exception.IdentitySAML2SSOException;
import org.wso2.carbon.identity.saml.inbound.exception.SAML2ClientException;
import org.wso2.carbon.identity.saml.inbound.model.SAMLSSOServiceProviderDO;
import org.wso2.carbon.identity.saml.inbound.request.SAMLSpInitRequest;
import org.wso2.carbon.identity.saml.inbound.util.SAMLSSOUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SPInitSSOAuthnRequestValidator {

    private static Logger log = LoggerFactory.getLogger(SPInitSSOAuthnRequestValidator.class);
    private SAMLMessageContext messageContext;


    public SPInitSSOAuthnRequestValidator(SAMLMessageContext messageContext) throws IdentityException {
        this.messageContext = messageContext;
    }

    /**
     * Validates the authentication request according to SAML SSO Web Browser Specification
     *
     * @return boolean
     * @throws IdentityException
     */
    public boolean validate(AuthnRequest authnReq) throws IdentityException, IOException {

        Issuer issuer = authnReq.getIssuer();
        Subject subject = authnReq.getSubject();

        //@TODO Decide whether we want this
        //Validate the version
        if (!(SAMLVersion.VERSION_20.equals(authnReq.getVersion()))) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid version in the SAMLRequest" + authnReq.getVersion());
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                            .VERSION_MISMATCH, "Invalid SAML Version " + "in Authentication Request. SAML Version" +
                            " should " +
                            "be equal to 2.0", messageContext.getAssertionConsumerURL()), SAMLSSOConstants
                            .Notification.EXCEPTION_STATUS,
                    SAMLSSOConstants.Notification.EXCEPTION_MESSAGE, authnReq.getAssertionConsumerServiceURL());
        }

        // Issuer MUST NOT be null
        if (StringUtils.isNotBlank(issuer.getValue())) {
            messageContext.setIssuer(issuer.getValue());
        } else if (StringUtils.isNotBlank(issuer.getSPProvidedID())) {
            messageContext.setIssuer(issuer.getSPProvidedID());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("SAML Request issuer validation failed. Issuer should not be empty");
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                    .REQUESTOR_ERROR, "Issuer/ProviderName " + "should not be empty in the Authentication Request" +
                    ".", authnReq.getAssertionConsumerServiceURL()));
        }

        // TODO

//        try {
//            if (!SAMLSSOUtil.isSAMLIssuerExists(issuer.getValue(),
//                    SAMLSSOUtil.getTenantDomainFromThreadLocal())) {
//                String message = "A Service Provider with the Issuer '" + issuer.getValue() + "' is not " +
//                        "registered. Service Provider should be registered in " + "advance";
//                if (log.isDebugEnabled()) {
//                    log.debug(message);
//                }
//                messageContext.setValid(false);
//                throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
//                        .REQUESTOR_ERROR, message, null));
//            }
//        } catch (UserStoreException e) {
//            if (log.isDebugEnabled()) {
//                log.debug("Error occurred while handling SAML2 SSO request", e);
//            }
//            messageContext.setValid(false);
//            String errorResp = SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes.IDENTITY_PROVIDER_ERROR,
//                    "Error occurred while handling SAML2 SSO request", null);
//            throw SAML2ClientException.error(errorResp, SAMLSSOConstants.Notification.EXCEPTION_STATUS,
//                    SAMLSSOConstants.Notification.EXCEPTION_MESSAGE, null);
//        } catch (IdentityException e) {
//            log.error("Error when processing the authentication request!", e);
//            messageContext.setValid(false);
//            String errorResp = SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
//                    .IDENTITY_PROVIDER_ERROR, "Error when processing the authentication request", null);
//            throw SAML2ClientException.error(errorResp, SAMLSSOConstants.Notification.EXCEPTION_STATUS,
//                    SAMLSSOConstants.Notification.EXCEPTION_MESSAGE, null);
//        }

        // Issuer Format attribute
        if ((StringUtils.isNotBlank(issuer.getFormat())) &&
                !(issuer.getFormat().equals(SAMLSSOConstants.Attribute.ISSUER_FORMAT))) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid Issuer Format attribute value " + issuer.getFormat());
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                    .REQUESTOR_ERROR, "Issuer Format attribute" + " value is invalid", authnReq
                    .getAssertionConsumerServiceURL()));
        }

        SAMLSSOServiceProviderDO spDO = SAMLSSOUtil.getServiceProviderConfig(messageContext);
        if (spDO != null) {
            messageContext.setSamlssoServiceProviderDO(spDO);
        } else {
            String msg = "A Service Provider with the Issuer '" + messageContext.getIssuer() + "' is not " +
                    "registered." + " Service Provider should be registered in advance.";
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                    .REQUESTOR_ERROR, msg, authnReq.getAssertionConsumerServiceURL()));
        }

        // Check for a Spoofing attack
        String acsUrl = authnReq.getAssertionConsumerServiceURL();
        boolean acsValidated = false;
        acsValidated = SAMLSSOUtil.validateACS(messageContext.getTenantDomain(), messageContext.getIssuer(), authnReq
                .getAssertionConsumerServiceURL());

        if (!acsValidated) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid ACS URL value " + acsUrl + " in the AuthnRequest message from " + spDO
                        .getIssuer() + "\n" + "Possibly an attempt for a spoofing attack from Provider " +
                        authnReq.getIssuer().getValue());
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                    .REQUESTOR_ERROR, "Invalid Assertion " + "Consumer Service URL in the Authentication " +
                    "Request" + ".", acsUrl));
        }


        //TODO : Validate the NameID Format
        if (subject != null && subject.getNameID() != null) {
            messageContext.setSubject(subject.getNameID().getValue());
        }

        // subject confirmation should not exist
        if (subject != null && subject.getSubjectConfirmations() != null &&
                !subject.getSubjectConfirmations().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid Request message. A Subject confirmation method found " + subject
                        .getSubjectConfirmations().get(0));
            }
            messageContext.setValid(false);
            throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                    .REQUESTOR_ERROR, "Subject Confirmation " + "methods should NOT be in the request.", authnReq
                    .getAssertionConsumerServiceURL()));
        }
        messageContext.setValid(true);
        messageContext.addParameter("forceAuth", authnReq.isForceAuthn());
        messageContext.addParameter("passiveAuth", authnReq.isPassive());
        Integer index = authnReq.getAttributeConsumingServiceIndex();
        //according the spec, should be an unsigned short
        if (index != null && !(index < 1)) {
            messageContext.setAttributeConsumingServiceIndex(index);
        }
        if (log.isDebugEnabled()) {
            log.debug("Authentication Request Validation is successful.");
        }
        validateFurther(authnReq);
        messageContext.setValid(true);
        return true;
    }


    private boolean validateFurther(AuthnRequest authnReq) throws IOException, IdentityException {
        SAMLSSOServiceProviderDO serviceProviderConfigs = messageContext.getSamlssoServiceProviderDO();
        if (serviceProviderConfigs.isDoValidateSignatureInRequests()) {
            // TODO
            List<String> idpUrlSet = new ArrayList<>();

            if (messageContext.getDestination() == null || !idpUrlSet.contains(messageContext.getDestination())) {
                String msg = "Destination validation for Authentication Request failed. " + "Received: [" +
                        messageContext.getDestination() + "]." + " Expected one in the list: [" + StringUtils
                        .join(idpUrlSet, ',') + "]";
                if (log.isDebugEnabled()) {
                    log.debug(msg);
                }
                throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                        .REQUESTOR_ERROR, msg, authnReq.getAssertionConsumerServiceURL()));
            }

            // validate the signature
            boolean isSignatureValid = validateAuthnRequestSignature(messageContext);

            if (!isSignatureValid) {
                String msg = "Signature validation for Authentication Request failed.";
                if (log.isDebugEnabled()) {
                    log.debug(msg);
                }
                throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                        .REQUESTOR_ERROR, msg, authnReq.getAssertionConsumerServiceURL()));
            }
        } else {
            //Validate the assertion consumer url,  only if request is not signed.
            String acsUrl = messageContext.getAssertionConsumerURL();
            if (StringUtils.isBlank(acsUrl) || !serviceProviderConfigs.getAssertionConsumerUrlList().contains
                    (acsUrl)) {
                String msg = "ALERT: Invalid Assertion Consumer URL value '" + acsUrl + "' in the " +
                        "AuthnRequest message from  the issuer '" + serviceProviderConfigs.getIssuer() +
                        "'. Possibly " + "an attempt for a spoofing attack";
                if (log.isDebugEnabled()) {
                    log.debug(msg);
                }
                throw SAML2ClientException.error(SAMLSSOUtil.buildErrorResponse(SAMLSSOConstants.StatusCodes
                        .REQUESTOR_ERROR, msg, authnReq.getAssertionConsumerServiceURL()));
            }
        }

        return true;
    }

    private boolean validateAuthnRequestSignature(SAMLMessageContext messageContext) {

        if (log.isDebugEnabled()) {
            log.debug("Validating SAML Request signature");
        }

        SAMLSSOServiceProviderDO serviceProvider = messageContext.getSamlssoServiceProviderDO();

        String alias = serviceProvider.getCertAlias();
        RequestAbstractType request = null;
        try {
            String decodedReq = null;

            if (messageContext.getIdentityRequest().isRedirect()) {
                decodedReq = SAMLSSOUtil.decode(((SAMLSpInitRequest) messageContext.getIdentityRequest()).getSamlRequest());
            } else {
                decodedReq = SAMLSSOUtil.decodeForPost(((SAMLSpInitRequest) messageContext.getIdentityRequest())
                        .getSamlRequest());
            }
            request = (RequestAbstractType) SAMLSSOUtil.unmarshall(decodedReq);
        } catch (IdentityException e) {
            if (log.isDebugEnabled()) {
                log.debug("Signature Validation failed for the SAMLRequest : Failed to unmarshall the SAML " +
                        "Assertion", e);
            }
        }

        try {
            if (messageContext.getIdentityRequest().isRedirect()) {
                // DEFLATE signature in Redirect Binding
                return validateDeflateSignature((SAMLSpInitRequest) messageContext.getIdentityRequest(), messageContext
                        .getIssuer(), alias, "");
            } else {
                // XML signature in SAML Request message for POST Binding
                return validateXMLSignature(request, alias, "");
            }
        } catch (IdentityException e) {
            if (log.isDebugEnabled()) {
                log.debug("Signature Validation failed for the SAMLRequest : Failed to validate the SAML Assertion", e);
            }
            return false;
        }
    }


    private boolean validateDeflateSignature(SAMLSpInitRequest request, String issuer,
                                             String alias, String domainName) throws IdentityException {
        try {
            return new SAML2HTTPRedirectDeflateSignatureValidator().validateSignature(request, issuer,
                    alias, domainName);

        } catch (org.opensaml.xml.security.SecurityException e) {
            log.error("Error validating deflate signature", e);
            return false;
        } catch (IdentitySAML2SSOException e) {
            log.warn("Signature validation failed for the SAML Message : Failed to construct the X509CredentialImpl for the alias " +
                    alias, e);
            return false;
        }
    }


    /**
     * Validate the signature of an assertion
     *
     * @param request    SAML Assertion, this could be either a SAML Request or a
     *                   LogoutRequest
     * @param alias      Certificate alias against which the signature is validated.
     * @param domainName domain name of the subject
     * @return true, if the signature is valid.
     */
    private boolean validateXMLSignature(RequestAbstractType request, String alias,
                                         String domainName) throws IdentityException {

        // TODO :
        return true;
//        if (request.getSignature() != null) {
//            try {
//                X509Credential cred = SAMLSSOUtil.getX509CredentialImplForTenant(domainName, alias);
//                return new DefaultSSOSigner().validateXMLSignature(request, cred, alias);
//            } catch (IdentitySAML2SSOException e) {
//                if (log.isDebugEnabled()) {
//                    log.debug("Signature validation failed for the SAML Message : Failed to construct the " +
//                            "X509CredentialImpl for the alias " + alias, e);
//                }
//            } catch (IdentityException e) {
//                if (log.isDebugEnabled()) {
//                    log.debug("Signature Validation Failed for the SAML Assertion : Signature is invalid.", e);
//                }
//            }
//        }
//        return false;
    }

    public FrameworkHandlerResponse validate(AuthenticationContext authenticationContext) throws RequestHandlerException {
        return null;
    }

    public String getName() {
        return null;
    }
}
