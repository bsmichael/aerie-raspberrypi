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

import org.eaa690.aerie.model.RateLimitRepository;
import org.eaa690.aerie.model.WeatherProductRepository;
import org.eaa690.aerie.service.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import org.eaa690.aerie.ssl.SSLUtilities;

/**
 * ServiceConfigTests.
 */
public class ServiceConfigTests {

    /**
     * PropertyService.
     */
    @Mock
    private PropertyService propertyService;

    /**
     * RestTemplateBuilder.
     */
    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    /**
     * RestTemplate.
     */
    @Mock
    private RestTemplate restTemplate;

    /**
     * WeatherProductRepository.
     */
    @Mock
    private WeatherProductRepository wpRepository;

    /**
     * RateLimitRepository.
     */
    @Mock
    private RateLimitRepository rlRepository;

    /**
     * SSLUtilities.
     */
    @Mock
    private SSLUtilities sslUtilities;

    /**
     * RosterManager.
     */
    @Mock
    private RosterManager rosterManager;

    /**
     * ServiceConfig.
     */
    private ServiceConfig serviceConfig;

    /**
     * Test setup.
     */
    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        Mockito.doReturn(restTemplateBuilder).when(restTemplateBuilder).setConnectTimeout(ArgumentMatchers.any());
        Mockito.doReturn(restTemplateBuilder).when(restTemplateBuilder).setReadTimeout(ArgumentMatchers.any());
        Mockito
                .doReturn(restTemplateBuilder)
                .when(restTemplateBuilder)
                .additionalMessageConverters(ArgumentMatchers.any(MappingJackson2HttpMessageConverter.class));
        Mockito.doReturn(restTemplate).when(restTemplateBuilder).build();

        serviceConfig = new ServiceConfig();
    }

    /**
     * Test loading of rest template bean.
     */
    @Test
    public void restTemplate() {
        Assert.assertNotNull(serviceConfig.restTemplate(restTemplateBuilder));
    }

    /**
     * Test loading of weather service bean.
     */
    @Test
    public void weatherService() {
        Assert
                .assertNotNull(
                        serviceConfig.weatherService(restTemplate, propertyService, sslUtilities,
                                wpRepository, rlRepository));
    }

    /**
     * Test loading of SSLUtilities bean.
     */
    @Test
    public void sslUtilities() {
        Assert.assertNotNull(serviceConfig.sslUtilities());
    }

    /**
     * Test loading of RosterManager bean.
     */
    @Test
    public void rosterManager() {
        Assert.assertNotNull(serviceConfig.rosterManager(propertyService));
    }

}