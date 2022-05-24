package com.sap.scimono.scim.system.tests.tg16.r5;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.scimono.client.SCIMResponse;
import com.sap.scimono.client.UnexpectedResponse;
import com.sap.scimono.entity.ErrorResponse;
import com.sap.scimono.entity.SAPUserExtension;
import com.sap.scimono.entity.User;
import com.sap.scimono.entity.patch.PatchBody;
import com.sap.scimono.entity.patch.PatchOperation;
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

import static com.sap.scimono.entity.definition.CoreUserAttributes.ACTIVE;
import static com.sap.scimono.entity.definition.CoreUserAttributes.EMAILS;
import static javax.ws.rs.core.Response.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserInActiveFlagTest extends SCIMHttpResponseCodeTest {
    private static final Logger logger = LoggerFactory.getLogger(UserInActiveFlagTest.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final GlobalIdGenerator guidGenerator = new GlobalIdGenerator();

    @RegisterExtension
    UserClientScimResponseExtension resourceAwareUserRequest = UserClientScimResponseExtension.forClearingAfterEachExecutions(userRequest);

    private final UserFailSafeClient userFailSafeClient = resourceAwareUserRequest.getFailSafeClient();

    @Test
    @DisplayName("Create unlocked user")
    public void createUnlockedUser() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_createUnlockedUser";
        final String email = "peter.lustig@create.unlocked";

        User validUser = TestData.buildInActiveUser(true, userName, uUid, email);
        SCIMResponse<User> scimResponse = resourceAwareUserRequest.createUser(validUser);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(scimResponse.get().getId()).get();
        boolean readActive = createdUser.isActive();

        assertTrue(readActive);

    }

    @Test
    @DisplayName("Create locked user")
    public void createLockedUser() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_createLockedUser";
        final String email = "peter.lustig@create.locked";

        User validUser = TestData.buildInActiveUser(false, userName, uUid, email);
        SCIMResponse<User> scimResponse = resourceAwareUserRequest.createUser(validUser);

        logger.info("Fetching user User: {}", userName);
        User createdUser = resourceAwareUserRequest.readSingleUser(scimResponse.get().getId()).get();
        boolean readActive = createdUser.isActive();

        assertFalse(readActive);

    }

    @Test
    @DisplayName("Deactivate user via PUT")
    public void deactivateUserViaPut() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_deactivateViaPut";
        final String email = "peter.lustig@deactivate.put";

        final boolean initialActive = true;
        final boolean updatedActive = false;

        toggleActivePut(uUid, userName, email, initialActive, updatedActive);

    }

    @Test
    @DisplayName("Activate user via PUT")
    public void activateUserViaPut() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_activateViaPut";
        final String email = "peter.lustig@activate.put";

        final boolean initialActive = false;
        final boolean updatedActive = true;

        toggleActivePut(uUid, userName, email, initialActive, updatedActive);

    }

    @Test
    @DisplayName("Activate user via PATCH")
    public void activateUserViaPatch() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_activateViaPatch";
        final String email = "peter.lustig@activate.patch";

        final boolean initialActive = false;
        final boolean updatedActive = true;

        toggleActiveFlagPatch(uUid, userName, email, initialActive, updatedActive);

    }

    @Test
    @DisplayName("Deactivate user via PATCH")
    public void deActivateUserViaPatch() {

        final String uUid = guidGenerator.getGuid();
        final String userName = "activeFlag_deActivateViaPatch";
        final String email = "peter.lustig@deActivate.patch";

        final boolean initialActive = true;
        final boolean updatedActive = false;

        toggleActiveFlagPatch(uUid, userName, email, initialActive, updatedActive);
    }

    private void toggleActiveFlagPatch(String uUid, String userName, String email, boolean initialActive, boolean updatedActive) {
        User validUser = TestData.buildInActiveUser(initialActive, userName, uUid, email);
        SCIMResponse<User> scimResponse = resourceAwareUserRequest.createUser(validUser);
        User createdUser = resourceAwareUserRequest.readSingleUser(scimResponse.get().getId()).get();
        boolean readActive = createdUser.isActive();
        String userId = createdUser.getId();

        assertEquals(initialActive, readActive);

        PatchBody patchBody = TestData.buildPatchBody(PatchOperation.Type.REPLACE, ACTIVE.scimName(), updatedActive);
        SCIMResponse<?> scimResponsePatch = resourceAwareUserRequest.patchUser(patchBody, userId);
        if (scimResponsePatch.isSuccess()) {
            scimResponse = resourceAwareUserRequest.readSingleUser(userId);
            User updatedUserRead = scimResponse.get();
            boolean activationStatus = updatedUserRead.isActive();
            assertEquals(updatedActive, activationStatus);
        }
    }

    private void toggleActivePut(String uUid, String userName, String email, boolean initialActive, boolean updatedActive) {
        User validUser = TestData.buildInActiveUser(initialActive, userName, uUid, email);
        SCIMResponse<User> scimResponse = resourceAwareUserRequest.createUser(validUser);

        User createdUser = resourceAwareUserRequest.readSingleUser(scimResponse.get().getId()).get();
        boolean readActive = createdUser.isActive();
        String userId = createdUser.getId();

        assertEquals(initialActive, readActive);
        SAPUserExtension.Builder sapExtBuild = new SAPUserExtension.Builder().setUserUuid(uUid);
        User updatedUser = new User.Builder(createdUser).setId(userId).setActive(updatedActive).addExtension(sapExtBuild.build()).build();

        SCIMResponse<User> updatedUserScimResponse = resourceAwareUserRequest.updateUser(updatedUser);
        User updatedUserRead = resourceAwareUserRequest.readSingleUser(updatedUserScimResponse.get().getId()).get();
        boolean nowActive = updatedUserRead.isActive();
        assertEquals(updatedActive, nowActive);
    }
}