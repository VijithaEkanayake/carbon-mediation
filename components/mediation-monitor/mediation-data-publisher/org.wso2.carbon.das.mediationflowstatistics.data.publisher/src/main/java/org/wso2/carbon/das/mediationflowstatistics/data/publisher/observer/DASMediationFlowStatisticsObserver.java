/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.das.mediationflowstatistics.data.publisher.observer;


import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.conf.MediationStatConfig;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.conf.RegistryPersistenceManager;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.publish.Publisher;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.mediation.flow.statistics.MediationFlowStatisticsObserver;
import org.wso2.carbon.mediation.flow.statistics.store.tree.data.StatisticDataHolder;
import org.wso2.carbon.mediation.statistics.*;


public class DASMediationFlowStatisticsObserver implements MediationFlowStatisticsObserver, TenantInformation {

    private static final Log log = LogFactory.getLog(DASMediationFlowStatisticsObserver.class);
    private AxisConfiguration axisConfiguration;
    private int tenantId = -1234;

    public DASMediationFlowStatisticsObserver() {
    }

    @Override
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("Shutting down the mediation statistics observer of BAM");
        }
    }

    @Override
    public void updateStatistics(StatisticDataHolder statisticSnapshot) {
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId,true);
            int tenantID = getTenantId();
            MediationStatConfig mediationStatConfig = new RegistryPersistenceManager().getEventingConfigData(tenantID);
            if (mediationStatConfig == null) {
                return;
            }
            process(statisticSnapshot,mediationStatConfig);
        } catch (Exception e) {
            log.error("failed to update statics from BAM publisher", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private void process(StatisticDataHolder statisticSnapshot, MediationStatConfig mediationStatConfig) {
        if (statisticSnapshot == null) {
            return;
        }
        Publisher.process(statisticSnapshot, mediationStatConfig);
    }

    @Override
    public int getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(int i) {
        tenantId = i;
    }

    public AxisConfiguration getTenantAxisConfiguration() {
        return axisConfiguration;
    }

    public void setTenantAxisConfiguration(AxisConfiguration axisConfiguration) {
        this.axisConfiguration = axisConfiguration;
    }
}
