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

package org.eaa690.aerie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.alexanderwe.bananaj.connection.MailChimpConnection;
import com.sendgrid.SendGrid;
import com.twilio.Twilio;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import org.eaa690.aerie.constant.CommonConstants;
import org.eaa690.aerie.constant.PropertyKeyConstants;
import org.eaa690.aerie.exception.ResourceNotFoundException;
import org.eaa690.aerie.model.RateLimitRepository;
import org.eaa690.aerie.model.WeatherProductRepository;
import org.eaa690.aerie.service.JotFormService;
import org.eaa690.aerie.service.MailChimpService;
import org.eaa690.aerie.service.PictureService;
import org.eaa690.aerie.service.PropertyService;
import org.eaa690.aerie.service.RosterService;
import org.eaa690.aerie.service.SlackService;
import org.eaa690.aerie.service.TinyURLService;
import org.eaa690.aerie.service.WeatherService;
import org.eaa690.aerie.ssl.SSLUtilities;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * ServiceConfig.
 */
@Configuration
public class ServiceConfig {

    /**
     * Creates a rest template with default timeout settings. The bean definition will be updated to accept timeout
     * parameters once those are part of the Customer settings.
     *
     * @param restTemplateBuilder RestTemplateBuilder
     *
     * @return Rest Template with request, read, and connection timeouts set
     */
    @Bean
    public RestTemplate restTemplate(
            final RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(CommonConstants.ONE_THOUSAND))
                .setReadTimeout(Duration.ofMillis(CommonConstants.TEN_THOUSAND))
                .additionalMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    /**
     * WeatherService.
     *
     * @param restTemplate RestTemplate
     * @param propertyService PropertyService
     * @param sslUtilities SSLUtilities
     * @param wpRepository WeatherProductRepository
     * @param rlRepository RateLimitRepository
     * @return WeatherService
     */
    @Bean
    public WeatherService weatherService(
            final RestTemplate restTemplate,
            final PropertyService propertyService,
            final SSLUtilities sslUtilities,
            final WeatherProductRepository wpRepository,
            final RateLimitRepository rlRepository) {
        final WeatherService weatherService = new WeatherService();
        weatherService.setRestTemplate(restTemplate);
        weatherService.setPropertyService(propertyService);
        weatherService.setSSLUtilities(sslUtilities);
        weatherService.setWeatherProductRepository(wpRepository);
        weatherService.setRateLimitRepository(rlRepository);
        return weatherService;
    }

    /**
     * HttpClient.
     *
     * @return HttpClient
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    /**
     * ObjectMapper.
     *
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * MailChimpConnection.
     *
     * @param propertyService PropertyService
     * @return MailChimpConnection
     */
    @Bean
    public MailChimpConnection mailChimpConnection(final PropertyService propertyService) {
        try {
            return new MailChimpConnection(propertyService
                    .get(PropertyKeyConstants.MAILCHIMP_API_KEY).getValue());
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * SendGrid.
     *
     * @param propertyService PropertyService
     * @return SendGrid
     */
    @Bean
    public SendGrid sendGrid(final PropertyService propertyService) {
        try {
            return new SendGrid(propertyService
                    .get(PropertyKeyConstants.SEND_GRID_EMAIL_API_KEY).getValue());
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * RosterService.
     *
     * @return RosterService
     */
    @Bean
    public RosterService rosterService() {
        return new RosterService();
    }

    /**
     * JotFormService.
     *
     * @return JotFormService
     */
    @Bean
    public JotFormService jotFormService() {
        return new JotFormService();
    }

    /**
     * TinyURLService.
     *
     * @return TinyURLService
     */
    @Bean
    public TinyURLService tinyUrlService() {
        return new TinyURLService();
    }

    /**
     * MailChimpService.
     *
     * @return MailChimpService
     */
    @Bean
    public MailChimpService mailChimpService() {
        return new MailChimpService();
    }

    /**
     * SSLUtilities.
     *
     * @return SSLUtilities
     */
    @Bean
    public SSLUtilities sslUtilities() {
        return new SSLUtilities();
    }

    /**
     * Membership Bot SlackSession.
     *
     * @return SlackSession
     */
    @Bean(name="membership")
    public SlackSession slackSession(final PropertyService propertyService, final SlackService slackService) {
        try {
            final SlackSession slackSession = SlackSessionFactory
                    .createWebSocketSlackSession(
                            propertyService.get(PropertyKeyConstants.MEMBERSHIP_SLACK_TOKEN_KEY).getValue());
            slackSession.connect();
            slackSession.addMessagePostedListener(slackService);
            return slackSession;
        } catch (IOException | ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Pictures Bot SlackSession.
     *
     * @return SlackSession
     */
    @Bean(name="pictures")
    public SlackSession slackSession(final PropertyService propertyService, final PictureService slackService) {
        try {
            final SlackSession slackSession = SlackSessionFactory
                    .createWebSocketSlackSession(
                            propertyService.get(PropertyKeyConstants.PICTURES_SLACK_TOKEN_KEY).getValue());
            slackSession.connect();
            slackSession.addMessagePostedListener(slackService);
            return slackSession;
        } catch (IOException | ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Initialize twillio.
     *
     * @param propertyService PropertyService
     */
    @Bean
    public Object twillio(final PropertyService propertyService) {
        try {
            Twilio.init(propertyService.get(PropertyKeyConstants.SMS_ACCOUNT_SID_KEY).getValue(),
                    propertyService.get(PropertyKeyConstants.SMS_AUTH_ID_KEY).getValue());
        } catch (ResourceNotFoundException e) {
            return null;
        }
        return null;
    }
}