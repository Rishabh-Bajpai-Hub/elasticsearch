/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.marvel.agent.settings;

import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.marvel.test.MarvelIntegTestCase;
import org.elasticsearch.node.Node;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.equalTo;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST, numDataNodes = 1)
public class MarvelSettingsTests extends MarvelIntegTestCase {

    private final TimeValue startUp = newRandomTimeValue();
    private final TimeValue interval = newRandomTimeValue();
    private final TimeValue indexStatsTimeout = newRandomTimeValue();
    private final String[] indices = randomStringArray();
    private final TimeValue clusterStateTimeout = newRandomTimeValue();
    private final TimeValue clusterStatsTimeout = newRandomTimeValue();
    private final TimeValue recoveryTimeout = newRandomTimeValue();
    private final Boolean recoveryActiveOnly = randomBoolean();
    private final String[] collectors = randomStringArray();
    private final TimeValue licenseGracePeriod = randomExpirationDelay();

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put(Node.HTTP_ENABLED, true)
                .put(marvelSettings())
                .build();
    }

    private Settings marvelSettings() {
        return Settings.builder()
                .put(MarvelSettings.STARTUP_DELAY, startUp)
                .put(MarvelSettings.INTERVAL, interval)
                .put(MarvelSettings.INDEX_STATS_TIMEOUT, indexStatsTimeout)
                .putArray(MarvelSettings.INDICES, indices)
                .put(MarvelSettings.CLUSTER_STATE_TIMEOUT, clusterStateTimeout)
                .put(MarvelSettings.CLUSTER_STATS_TIMEOUT, clusterStatsTimeout)
                .put(MarvelSettings.INDEX_RECOVERY_TIMEOUT, recoveryTimeout)
                .put(MarvelSettings.INDEX_RECOVERY_ACTIVE_ONLY, recoveryActiveOnly)
                .putArray(MarvelSettings.COLLECTORS, collectors)
                .build();
    }

    @Test
    public void testMarvelSettings() throws Exception {
        logger.info("--> testing marvel settings service initialization");
        for (final MarvelSettings marvelSettings : internalCluster().getInstances(MarvelSettings.class)) {
            assertThat(marvelSettings.startUpDelay().millis(), equalTo(startUp.millis()));
            assertThat(marvelSettings.interval().millis(), equalTo(interval.millis()));
            assertThat(marvelSettings.indexStatsTimeout().millis(), equalTo(indexStatsTimeout.millis()));
            assertArrayEquals(marvelSettings.indices(), indices);
            assertThat(marvelSettings.clusterStateTimeout().millis(), equalTo(clusterStateTimeout.millis()));
            assertThat(marvelSettings.clusterStatsTimeout().millis(), equalTo(clusterStatsTimeout.millis()));
            assertThat(marvelSettings.recoveryTimeout().millis(), equalTo(recoveryTimeout.millis()));
            assertThat(marvelSettings.recoveryActiveOnly(), equalTo(recoveryActiveOnly));
            assertArrayEquals(marvelSettings.collectors(), collectors);
        }

        logger.info("--> testing marvel dynamic settings update");
        for (String setting : MarvelSettings.dynamicSettings().keySet()) {
            Object updated = null;
            Settings.Builder transientSettings = Settings.builder();

            if (setting.endsWith(".*")) {
                setting = setting.substring(0, setting.lastIndexOf('.'));
            }

            switch (setting) {
                case MarvelSettings.INTERVAL:
                case MarvelSettings.INDEX_STATS_TIMEOUT:
                case MarvelSettings.INDICES_STATS_TIMEOUT:
                case MarvelSettings.CLUSTER_STATE_TIMEOUT:
                case MarvelSettings.CLUSTER_STATS_TIMEOUT:
                case MarvelSettings.INDEX_RECOVERY_TIMEOUT:
                    updated = newRandomTimeValue();
                    transientSettings.put(setting, updated);
                    break;
                case MarvelSettings.INDEX_RECOVERY_ACTIVE_ONLY:
                    updated = randomBoolean();
                    transientSettings.put(setting, updated);
                    break;
                case MarvelSettings.INDICES:
                    updated = randomStringArray();
                    transientSettings.putArray(setting, (String[]) updated);
                    break;
                default:
                    fail("unknown dynamic setting [" + setting +"]");
            }

            logger.info("--> updating {} to value [{}]", setting, updated);
            assertAcked(prepareRandomUpdateSettings(transientSettings.build()).get());

            // checking that the value has been correctly updated on all marvel settings services
            final Object expected = updated;
            final String finalSetting = setting;
            assertBusy(new Runnable() {
                @Override
                public void run() {
                    for (final MarvelSettings marvelSettings : internalCluster().getInstances(MarvelSettings.class)) {
                        MarvelSetting current = marvelSettings.getSetting(finalSetting);
                        Object value = current.getValue();

                        logger.info("--> {} in {}", current, marvelSettings);
                        if (current instanceof MarvelSetting.TimeValueSetting) {
                            assertThat(((TimeValue) value).millis(), equalTo(((TimeValue) expected).millis()));

                        } else if (current instanceof MarvelSetting.BooleanSetting) {
                            assertThat((Boolean) value, equalTo((Boolean) expected));

                        } else if (current instanceof MarvelSetting.StringSetting) {
                            assertThat((String) value, equalTo((String) expected));

                        } else if (current instanceof MarvelSetting.StringArraySetting) {
                            assertArrayEquals((String[]) value, (String[]) expected);
                        } else {
                            fail("unable to check value for unknown dynamic setting [" + finalSetting + "]");
                        }
                    }
                }
            });
        }
    }

    private ClusterUpdateSettingsRequestBuilder prepareRandomUpdateSettings(Settings updateSettings) {
        ClusterUpdateSettingsRequestBuilder requestBuilder = client().admin().cluster().prepareUpdateSettings();
        if (randomBoolean()) {
            requestBuilder.setTransientSettings(updateSettings);
        } else {
            requestBuilder.setPersistentSettings(updateSettings);
        }
        return requestBuilder;
    }

    private TimeValue newRandomTimeValue() {
        return TimeValue.parseTimeValue(randomFrom("30m", "1h", "3h", "5h", "7h", "10h", "1d"), null, getClass().getSimpleName());
    }

    private String[] randomStringArray() {
        final int size = scaledRandomIntBetween(1, 10);
        String[] items = new String[size];

        for (int i = 0; i < size; i++) {
            items[i] = randomAsciiOfLength(5);
        }
        return items;
    }

    private TimeValue randomExpirationDelay() {
        return randomBoolean() ? newRandomTimeValue() : TimeValue.timeValueHours(randomIntBetween(-10, 10) * 24);
    }
}
