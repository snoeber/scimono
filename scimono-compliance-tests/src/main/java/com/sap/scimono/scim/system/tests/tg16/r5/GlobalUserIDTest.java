package com.sap.scimono.scim.system.tests.tg16.r5;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.scimono.client.SCIMResponse;
import com.sap.scimono.client.UnexpectedResponse;
import com.sap.scimono.entity.ErrorResponse;
import com.sap.scimono.entity.SAPUserExtension;
import com.sap.scimono.entity.User;
import com.sap.scimono.exception.SCIMException;
import com.sap.scimono.scim.system.tests.SCIMHttpResponseCodeTest;
import com.sap.scimono.scim.system.tests.extensions.UserClientScimResponseExtension;
import com.sap.scimono.scim.system.tests.extensions.UserFailSafeClient;
import com.sap.scimono.scim.system.tests.util.GlobalIdGenerator;
import com.sap.scimono.scim.system.tests.util.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;

import static javax.ws.rs.core.Response.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class GlobalUserIDTest extends SCIMHttpResponseCodeTest {
    private static final Logger logger = LoggerFactory.getLogger(GlobalUserIDTest.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final GlobalIdGenerator guidGenerator = new GlobalIdGenerator();

    @RegisterExtension
    UserClientScimResponseExtension resourceAwareUserRequest = UserClientScimResponseExtension.forClearingAfterEachExecutions(userRequest);

    private final UserFailSafeClient userFailSafeClient = resourceAwareUserRequest.getFailSafeClient();

    @Test
    @DisplayName("Only accept Global User IDs in the format ^[-+_/:.A-Za-z0-9]{1,36}$")
    public void testValidInvalidUserUuidS() {

        final String[] validUuids = new String[]{
                guidGenerator.getGuid(), guidGenerator.getGuid()
        };
        final String[] inValidUuids = new String[]{
                // no backslash
                "zVrbB:5UGg+8_-BYM+2+BF____\\",
                // no @sign
                "no.At@Sign.allowed",
                // too long
                guidGenerator.getGuid(36) + "tooLong"
        };

        final String validUserNamePrefix = "validGlobalUserId_165_",
                     invalidUserNamePrefix = "invalidGlobalUserId_165_",
                     email_suffix = "@test.com";

        // check user doesn't exist and delete otherwise
        List<User> userPresent = userFailSafeClient.getAllByFilter("emails.value eq \"personal_joro@sap.com\"");
        if (userPresent.size() > 0) {
            for (User user : userPresent) {
                logger.info("User {}, already existed - deleting", user.getId());
                userFailSafeClient.delete(user.getId());
            }
        }

        User[] validUsers = new User[validUuids.length];
        SCIMResponse<User>[] validScimResponses = new SCIMResponse[validUsers.length];

        User[] invalidUsers = new User[inValidUuids.length];
        SCIMResponse<User>[] invalidScimResponses = new SCIMResponse[invalidUsers.length];

        for (int i = 0; i < validUuids.length; i++) {
            logger.info("Creating valid user with uuid: {}", validUuids[i]);
            String userName = validUserNamePrefix + i;
            String email = validUserNamePrefix + i + email_suffix;
            checkUserDoesntExistAndDelete(userName, email);

            validUsers[i] = TestData.buildSAPExtensionUser(userName, validUuids[i], email);
            validScimResponses[i] = resourceAwareUserRequest.createUser(validUsers[i]);
        }
        for (int i = 0; i < inValidUuids.length; i++) {
            logger.info("Creating invalid user with uuid: {}", inValidUuids[i]);

            String userName = invalidUserNamePrefix + i;
            String email = invalidUserNamePrefix + i + email_suffix;
            checkUserDoesntExistAndDelete(userName, email);

            invalidUsers[i] = TestData.buildSAPExtensionUser(userName, inValidUuids[i], email);
            invalidScimResponses[i] = resourceAwareUserRequest.createUser(invalidUsers[i]);
        }

        logger.info("Fetching valid users {}", (Object[]) validUsers);
        String[] readUUids = new String[validUuids.length];
        for (int i = 0; i < validScimResponses.length; i++) {
            readUUids[i] = (String) resourceAwareUserRequest.readSingleUser(validScimResponses[i].get().getId()).get().getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        }
        logger.info("Validating scimtype");
        ErrorResponse[] errorResponses = new ErrorResponse[invalidScimResponses.length];
        try {
            for (int i = 0; i < invalidScimResponses.length; i++) {
                errorResponses[i] = mapper.readValue(invalidScimResponses[i].getError().asUnknownException(), ErrorResponse.class);
            }
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
        for (int i = 0; i < validScimResponses.length; i++) {
            SCIMResponse<User> scimResponse = validScimResponses[i];
            String readUUid = readUUids[i];
            String validUUid = validUuids[i];

            assertAll("valid uuid according to regex",
                    () -> assertEquals(scimResponse.getStatusCode(), CREATED.getStatusCode()),
                    () -> assertEquals(readUUid, validUUid));
        }

        for (int i = 0; i < invalidScimResponses.length; i++) {
            SCIMResponse<User> scimResponse = invalidScimResponses[i];
            ErrorResponse errorResponse = errorResponses[i];

            assertAll("uuid according to regex",
                    () -> assertEquals(scimResponse.getStatusCode(), BAD_REQUEST.getStatusCode()),
                    () -> assertEquals(SCIMException.Type.INVALID_VALUE.toString(), errorResponse.getScimType())
            );
        }
    }

    @Test
    @DisplayName("DO NOT allow to remove the Global User ID once set (i.e., setting to a blank value is prohibited)")
    public void testNoRemovalOfGlobalUserID() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "prohibtDeletionOfGUID";
        final String email = "peter.lustig@need.uuid";

        // check user doesn't exist and delete otherwise
        checkUserDoesntExistAndDelete(userName, email);

        User validUser = TestData.buildSAPExtensionUser(userName, uUid, email);
        SCIMResponse<User> validScimResponse = resourceAwareUserRequest.createUser(validUser);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(validScimResponse.get().getId()).get();
        String readUUid = (String) createdUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        String userId = createdUser.getId();

        assertEquals(readUUid, uUid);

        logger.info("Setting uuid to blank");

        SAPUserExtension.Builder sapExtBuild = new SAPUserExtension.Builder().setUserUuid("");
        User emptyUUidUser = new User.Builder(validUser).setId(userId).addExtension(sapExtBuild.build()).build();

        SCIMResponse<User> emptyUuidUserResponse = resourceAwareUserRequest.updateUser(emptyUUidUser);
        logger.info(emptyUuidUserResponse.toString());

        String errorResponseString = emptyUuidUserResponse.getError().asUnknownException();
        try {
            ErrorResponse errorResponse = mapper.readValue(errorResponseString, ErrorResponse.class);
            assertAll("uuid not set to blank",
                    () -> assertEquals(emptyUuidUserResponse.getStatusCode(), BAD_REQUEST.getStatusCode()),
                    () -> assertEquals(errorResponse.getScimType(), "invalidValue"));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Store the Global User ID as-is (no conversions are allowed)")
    public void testStoreGlobalUserIDAsIs() {

        final String uUid = guidGenerator.getGuid(32);
        final String userName = "asIsStorageGUID";
        final String email = "peter.lustig@asIs.uuid";

        // check user doesn't exist and delete otherwise
        checkUserDoesntExistAndDelete(userName, email);

        User validUser = TestData.buildSAPExtensionUser(userName, uUid, email);
        SCIMResponse<User> validScimResponse = resourceAwareUserRequest.createUser(validUser);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(validScimResponse.get().getId()).get();
        String readUUid = (String) createdUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");

        assertEquals(readUUid, uUid);
    }

    @Test
    @DisplayName("Handle the Global User ID as case-sensitive")
    public void testCaseSensitiveGlobalUserID() {

        final String uUid = guidGenerator.getGuid(32);
        final String userName = "caseSensitiveGUIDHandling";
        final String email = "peter.lustig@caseSensitive.uuid";

        // check user doesn't exist and delete otherwise
        checkUserDoesntExistAndDelete(userName, email);

        User validUser = TestData.buildSAPExtensionUser(userName, uUid, email);
        SCIMResponse<User> validScimResponse = resourceAwareUserRequest.createUser(validUser);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(validScimResponse.get().getId()).get();
        String userId = createdUser.getId();
        String readUUid = (String) createdUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        assertEquals(readUUid, uUid);

        logger.info("Setting uuid to upper case and update");

        SAPUserExtension.Builder sapExtBuild = new SAPUserExtension.Builder().setUserUuid(uUid.toUpperCase());
        User upperCaseUUidUser = new User.Builder(validUser).setId(userId).addExtension(sapExtBuild.build()).build();

        SCIMResponse<User> upperCaseUserResponse = resourceAwareUserRequest.updateUser(upperCaseUUidUser);
        logger.info(upperCaseUserResponse.toString());

        logger.info("Fetching user User: {}", userName);
        User updatedUser = resourceAwareUserRequest.readSingleUser(validScimResponse.get().getId()).get();
        String updatedUUid = (String) updatedUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        assertEquals(updatedUUid, uUid.toUpperCase());

    }


    @Test
    @DisplayName("Global User ID is unique within the application tenant - two different user accounts MUST NOT have the same Global User ID")
    public void testGlobalUserIDIsUnique() {

        String uuid = guidGenerator.getGuid();
        final ArrayList<User> users = new ArrayList<>(2);
        users.add(TestData.buildSAPExtensionUser("guidIsUnique_First", uuid, "user1@uniqueInTenant.uuid"));
        users.add(TestData.buildSAPExtensionUser("guidIsUnique_Second", uuid, "user2@uniqueInTenant.uuid"));

        ArrayList<SCIMResponse<User>> scimResponses = new ArrayList<>(2);

        for (User user : users) {
            checkUserDoesntExistAndDelete(user.getUserName(), user.getPrimaryOrFirstEmail().isPresent()
                    ? user.getPrimaryOrFirstEmail().get().getValue() : "");
            scimResponses.add(resourceAwareUserRequest.createUser(user));
        }

        User validUser = resourceAwareUserRequest.readSingleUser(scimResponses.get(0).get().getId()).get();
        String createdValidUUid = (String) validUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");

        //check first user is created with uuid
        assertEquals(createdValidUUid, uuid);

        try {
            ErrorResponse errorResponse = mapper.readValue(scimResponses.get(1).getError().asUnknownException(), ErrorResponse.class);

            assertAll("uuid only set once",
                    () -> assertEquals(CONFLICT.getStatusCode(), scimResponses.get(1).getStatusCode()),
                    () -> assertEquals(SCIMException.Type.UNIQUENESS.toString(), errorResponse.getScimType())
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Global User ID unique in HISTORY of application tenant - a Global User ID cannot be reused")
    public void testGlobalUserIDIsUniqueInHistory() {

        String uuid = guidGenerator.getGuid();
        final ArrayList<User> users = new ArrayList<>(2);
        users.add(TestData.buildSAPExtensionUser("guidHistoryIsUnique_First", uuid, "user1@uniqueInTenantHistory.uuid"));
        users.add(TestData.buildSAPExtensionUser("guidHistoryIsUnique_Second", uuid, "user2@uniqueInTenantHistory.uuid"));

        SCIMResponse<User> createScimResponse, createOtherWithSameIdScimResponse;
        SCIMResponse<Void> deleteScimResponse;

        for (User user : users) {
            checkUserDoesntExistAndDelete(user.getUserName(), user.getPrimaryOrFirstEmail().isPresent()
                    ? user.getPrimaryOrFirstEmail().get().getValue() : "");
        }

        // create user
        createScimResponse = resourceAwareUserRequest.createUser(users.get(0));
        assertEquals(CREATED.getStatusCode(), createScimResponse.getStatusCode());

        // delete user
        deleteScimResponse = resourceAwareUserRequest.deleteUser(createScimResponse.get().getId());
        assertEquals(NO_CONTENT.getStatusCode(), deleteScimResponse.getStatusCode());

        // delete another user with same uuid
        createOtherWithSameIdScimResponse = resourceAwareUserRequest.createUser(users.get(1));
        assertNotEquals(CREATED.getStatusCode(), createOtherWithSameIdScimResponse.getStatusCode());

        try {
            if (!createOtherWithSameIdScimResponse.isSuccess()) {
                UnexpectedResponse notCreatedExc = createOtherWithSameIdScimResponse.getError();
                if (notCreatedExc != null) {
                    ErrorResponse errorResponse = mapper.readValue(notCreatedExc.asUnknownException(), ErrorResponse.class);
                    assertAll("uuid only not to be reused in history",
                            () -> assertEquals(Integer.valueOf(errorResponse.getStatus()), CONFLICT.getStatusCode()),
                            () -> assertEquals(SCIMException.Type.UNIQUENESS.toString(), errorResponse.getScimType())
                    );
                }
            }
        } catch (JsonProcessingException | WebApplicationException e) {
            logger.error(e.getMessage());
        }
    }

    @Test
    @DisplayName("Support updating the Global User ID")
    public void testUpdateOfGlobalID() {
        final String uUid = guidGenerator.getGuid();
        final String newUUid = guidGenerator.getGuid();
        final String userName = "supportUpdateOfGuid";
        final String email = "peter.lustig@update.uuid";

        // check user doesn't exist and delete otherwise
        checkUserDoesntExistAndDelete(userName, email);

        User user = TestData.buildSAPExtensionUser(userName, uUid, email);
        SCIMResponse<User> scimResponse = resourceAwareUserRequest.createUser(user);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(scimResponse.get().getId()).get();
        String readUUid = (String) createdUser.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        String userId = createdUser.getId();
        assertEquals(readUUid, uUid);

        logger.info("Setting uuid to a new value");

        SAPUserExtension.Builder sapExtBuild = new SAPUserExtension.Builder().setUserUuid(newUUid);
        User updatedUser = new User.Builder(user).setId(userId).addExtension(sapExtBuild.build()).build();

        SCIMResponse<User> updatedUserScimResponse = resourceAwareUserRequest.updateUser(updatedUser);
        logger.info(updatedUserScimResponse.toString());

        logger.info("Fetching user User: {}", userName);
        User updatedUserRead = resourceAwareUserRequest.readSingleUser(updatedUserScimResponse.get().getId()).get();
        String updatedUuid = (String) updatedUserRead.getExtensions().get("urn:ietf:params:scim:schemas:extension:sap:2.0:User").getAttribute("userUuid");
        assertEquals(updatedUuid, newUUid);
    }

    private void checkUserDoesntExistAndDelete(String userName, String email) {
        List<User> userPresent = userFailSafeClient.getAllByFilter("emails.value eq \"" + email + " \"");
        if (userPresent.size() > 0) {
            logger.info("User {}, already existed - deleting", userName);
            userFailSafeClient.delete(userPresent.get(0).getId());
        }
    }
}
