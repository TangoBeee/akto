package com.akto.usage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class UsageMetricHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricHandler.class);

    public static void calcAndSyncAccountUsage(int accountId){
        try {
            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId)
            );

            if (organization == null) {
                loggerMaker.errorAndAddToDb("Organization not found for account: " + accountId, LogDb.DASHBOARD);
                return;
            }

            loggerMaker.infoAndAddToDb(String.format("Measuring usage for %s / %d ", organization.getName(), accountId), LogDb.DASHBOARD);

            String organizationId = organization.getId();

            for (MetricTypes metricType : MetricTypes.values()) {

                UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, metricType)
                );

                if (usageMetricInfo == null) {
                    usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
                    UsageMetricInfoDao.instance.insertOne(usageMetricInfo);
                }

                int syncEpoch = usageMetricInfo.getSyncEpoch();
                int measureEpoch = usageMetricInfo.getMeasureEpoch();

                // Reset measureEpoch every month
                if (Context.now() - measureEpoch > UsageMetricInfo.MEASURE_PERIOD) {
                    if (syncEpoch > Context.now() - 86400) {
                        measureEpoch = Context.now();

                        UsageMetricInfoDao.instance.updateOne(
                                UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                                Updates.set(UsageMetricInfo.MEASURE_EPOCH, measureEpoch)
                        );
                    }

                }

                AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                        AccountSettingsDao.generateFilter()
                );
                String dashboardMode = DashboardMode.getDashboardMode().toString();
                String dashboardVersion = accountSettings.getDashboardVersion();

                UsageMetric usageMetric = new UsageMetric(
                        organizationId, accountId, metricType, syncEpoch, measureEpoch,
                        dashboardMode, dashboardVersion
                );

                //calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);
                usageMetric.setAktoSaveEpoch(Context.now());

                UsageMetricsDao.instance.insertOne(usageMetric);
                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);

                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);

                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
                loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                                usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(), usageMetric.getMetricType().toString()),
                        LogDb.DASHBOARD
                );
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()), LogDb.DASHBOARD);
        }
    }

    public static HashMap<String, FeatureAccess> updateFeatureMapWithLocalUsageMetrics(HashMap<String, FeatureAccess> featureWiseAllowed, Set<Integer> accounts){

        if (featureWiseAllowed == null) {
            featureWiseAllowed = new HashMap<>();
        }

        // since an org can have multiple accounts, we need to consolidate the usage.
        Map<String, FeatureAccess> consolidatedOrgUsage = UsageMetricsDao.instance.findLatestUsageMetricsForOrganization(accounts);

        for (Map.Entry<String, FeatureAccess> entry : featureWiseAllowed.entrySet()) {
            String featureLabel = entry.getKey();
            FeatureAccess featureAccess = entry.getValue();

            if (consolidatedOrgUsage.containsKey(featureLabel)) {
                FeatureAccess orgUsage = consolidatedOrgUsage.get(featureLabel);
                featureAccess.setUsage(orgUsage.getUsage());

                if(!featureAccess.checkBooleanOrUnlimited() && featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                    if(featureAccess.getOverageFirstDetected() == -1){
                        featureAccess.setOverageFirstDetected(orgUsage.getOverageFirstDetected());
                    }
                } else {
                    featureAccess.setOverageFirstDetected(-1);
                }
                featureWiseAllowed.put(featureLabel, featureAccess);
            }
        }
        return featureWiseAllowed;
    }
}
