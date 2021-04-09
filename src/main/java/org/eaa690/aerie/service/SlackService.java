/*
 *  Copyright (C) 2021 Gwinnett County Experimental Aircraft Association
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.eaa690.aerie.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import org.eaa690.aerie.constant.PropertyKeyConstants;
import org.eaa690.aerie.exception.ResourceNotFoundException;
import org.eaa690.aerie.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * SlackService.
 */
@Service("membershipSlackService")
public class SlackService implements SlackMessagePostedListener {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackService.class);

    /**
     * SimpleDateFormat.
     */
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");

    /**
     * PropertyService.
     */
    @Autowired
    private PropertyService propertyService;

    /**
     * JotFormService.
     */
    @Autowired
    private JotFormService jotFormService;

    /**
     * SlackSession
     */
    @Autowired
    @Qualifier("membership")
    private SlackSession slackSession;

    /**
     * Sets PropertyService.
     * Note: mostly used for unit test mocks
     *
     * @param value PropertyService
     */
    @Autowired
    public void setPropertyService(final PropertyService value) {
        propertyService = value;
    }

    /**
     * Sets SlackSession.
     * Note: mostly used for unit test mocks
     *
     * @param value SlackSession
     */
    @Autowired
    public void setSlackSession(final SlackSession value) {
        slackSession = value;
    }

    /**
     * Sets JotFormService.
     * Note: mostly used for unit test mocks
     *
     * @param value JotFormService
     */
    @Autowired
    public void setJotFormService(final JotFormService value) {
        jotFormService = value;
    }

    /**
     * Sends new member message.
     *
     * @param member Member
     */
    public void sendNewMembershipMsg(final Member member) {
        if (member != null && member.getSlack() != null && member.slackEnabled()) {
            try {
                String to = member.getSlack();
                if (Boolean.parseBoolean(
                        propertyService.get(PropertyKeyConstants.SLACK_TEST_MODE_ENABLED_KEY).getValue())) {
                    to = propertyService.get(PropertyKeyConstants.SLACK_TEST_MODE_RECIPIENT_KEY).getValue();
                }
                if (Boolean.parseBoolean(propertyService.get(PropertyKeyConstants.SLACK_ENABLED_KEY).getValue())) {
                    sendMessage(getMessage(member, PropertyKeyConstants.SLACK_NEW_MEMBER_MSG_KEY), to);
                }
            } catch (ResourceNotFoundException ex) {
                LOGGER.error(ex.getMessage());
            }
        }
    }

    /**
     * Sends membership renewal message.
     *
     * @param member Member
     */
    public void sendRenewMembershipMsg(final Member member) {
        if (member != null && member.getSlack() != null && member.slackEnabled()) {
            try {
                String to = member.getSlack();
                if (Boolean.parseBoolean(
                        propertyService.get(PropertyKeyConstants.SLACK_TEST_MODE_ENABLED_KEY).getValue())) {
                    to = propertyService.get(PropertyKeyConstants.SLACK_TEST_MODE_RECIPIENT_KEY).getValue();
                }
                sendMessage(getMessage(member, PropertyKeyConstants.SLACK_RENEW_MEMBER_MSG_KEY), to);
            } catch (ResourceNotFoundException ex) {
                LOGGER.error(ex.getMessage());
            }
        }
    }

    /**
     * Processes messages received by membership slack bot.
     *
     * @param event SlackMessagePosted
     * @param session SlackSession
     */
    @Override
    public void onEvent(final SlackMessagePosted event, final SlackSession session) {
        // Ignore bot user messages
        if (session.sessionPersona().getId().equals(event.getSender().getId())) {
            return;
        }
        final String message = event.getMessageContent();
        final String user = event.getUser().getUserName();
        final String msg = String.format(
                "Slack message received: user [%s]; message [%s]",
                user,
                message);
        LOGGER.info(msg);
    }

    /**
     * Gets all Slack users.
     *
     * @return list of users
     */
    public List<String> allSlackUsers() throws ResourceNotFoundException {
        final List<String> users = new ArrayList<>();
        slackSession
                .getUsers()
                .stream()
                .forEach(user -> users.add(user.getRealName() + "|" + user.getUserName()));
        return users;
    }

    /**
     * Builds message to be sent to member.
     *
     * @param member Member
     * @param msgKey message key
     * @return message
     */
    private String getMessage(final Member member, final String msgKey) {
        try {
            final String expiration = member.getExpiration() != null ?
                    sdf.format(member.getExpiration()) : sdf.format(new Date());
            return propertyService
                    .get(msgKey)
                    .getValue()
                    .replaceAll("\\{\\{firstName\\}\\}", member.getFirstName())
                    .replaceAll("\\{\\{lastName\\}\\}", member.getLastName())
                    .replaceAll("\\{\\{expirationDate\\}\\}", expiration)
                    .replaceAll("\\{\\{url\\}\\}", jotFormService.buildRenewMembershipUrl(member));
        } catch (ResourceNotFoundException e) {
            LOGGER.error("Error", e);
        }
        return null;
    }

    /**
     * Sends a message via the Slack bot.
     *
     * @param msg message to be sent
     * @param slackUserName Slack User Name
     * @throws ResourceNotFoundException when properties are not found
     */
    private void sendMessage(final String msg, final String slackUserName) throws ResourceNotFoundException {
        if (Boolean.parseBoolean(propertyService.get(PropertyKeyConstants.SLACK_ENABLED_KEY).getValue())) {
            LOGGER.info(String.format("Sending %s to %s", msg, slackUserName));
            slackSession.sendMessageToUser(slackSession.findUserByUserName(slackUserName), msg, null);
        }
    }

}