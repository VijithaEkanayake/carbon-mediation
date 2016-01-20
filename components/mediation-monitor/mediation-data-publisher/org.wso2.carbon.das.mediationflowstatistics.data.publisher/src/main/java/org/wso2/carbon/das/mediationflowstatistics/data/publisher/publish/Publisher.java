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
package org.wso2.carbon.das.mediationflowstatistics.data.publisher.publish;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.das.data.publisher.util.DASDataPublisherConstants;
import org.wso2.carbon.das.data.publisher.util.PublisherUtil;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.conf.EventPublisherConfig;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.conf.MediationStatConfig;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.conf.Property;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.util.MediationDataPublisherConstants;
import org.wso2.carbon.das.mediationflowstatistics.data.publisher.util.PublisherUtils;
import org.wso2.carbon.databridge.agent.thrift.AsyncDataPublisher;
import org.wso2.carbon.databridge.agent.thrift.exception.AgentException;
import org.wso2.carbon.databridge.agent.thrift.lb.DataPublisherHolder;
import org.wso2.carbon.databridge.agent.thrift.lb.LoadBalancingDataPublisher;
import org.wso2.carbon.databridge.agent.thrift.lb.ReceiverGroup;
import org.wso2.carbon.databridge.agent.thrift.util.DataPublisherUtil;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.mediation.flow.statistics.store.tree.data.StatisticDataHolder;

import java.util.ArrayList;
import java.util.List;


public class Publisher {


    private static Log log = LogFactory.getLog(Publisher.class);
    private static boolean isStreamDefinitionAlreadyExist = false;

    public static void process(StatisticDataHolder statisticSnapshot, MediationStatConfig mediationStatConfig) {
        List<String> metaDataKeyList = new ArrayList<String>();
        List<String> metaDataValueList = new ArrayList<String>();

        List<Object> eventData = new ArrayList<Object>();

        addEventData(eventData, statisticSnapshot);
        addMetaData(metaDataKeyList, metaDataValueList, mediationStatConfig);

        publishToAgent(eventData, metaDataKeyList, metaDataValueList, mediationStatConfig);
    }


    private static void addMetaData(List<String> metaDataKeyList, List<String> metaDataValueList,
                                    MediationStatConfig mediationStatConfig) {
        metaDataValueList.add(PublisherUtil.getHostAddress());
        Property[] properties = mediationStatConfig.getProperties();
        if (properties != null) {
            for (Property property : properties) {
                if (property.getKey() != null && !property.getKey().isEmpty()) {
                    metaDataKeyList.add(property.getKey());
                    metaDataValueList.add(property.getValue());
                }
            }
        }
    }


    private static void addEventData(List<Object> eventData, StatisticDataHolder statisticSnapshot) {
        eventData.add(statisticSnapshot.getTimeStamp());
        eventData.add(statisticSnapshot.getMessageFlowId());
        eventData.add(statisticSnapshot.getComponentType());
        eventData.add(statisticSnapshot.getComponentId());
        eventData.add(statisticSnapshot.getProcessingTime());
        eventData.add(statisticSnapshot.getFaultCount());
        eventData.add(statisticSnapshot.getStatisticTree());
    }




    private static void publishToAgent(List<Object> eventData,
                                       List<String> metaDataKeyList,
                                       List<String> metaDataValueList,
                                       MediationStatConfig mediationStatConfig) {

        String streamId = null;

        try {
            String serverUrl = "tcp://127.0.0.1:7611";
            String userName = "admin";
            String passWord = "admin";
            Object[] metaData = metaDataKeyList.toArray();

            StreamDefinition streamDef = getEventStreamDefinition(mediationStatConfig, metaData);
            String key = serverUrl + "_" + userName
                    + "_" + passWord + "_" + streamDef.getName();
            EventPublisherConfig eventPublisherConfig = PublisherUtils.getEventPublisherConfig(key);
            if (!mediationStatConfig.isLoadBalancingEnabled()) {
                AsyncDataPublisher dataPublisher = null;
                try {
                    if (eventPublisherConfig == null) {
                        synchronized (Publisher.class) {
                            eventPublisherConfig = new EventPublisherConfig();
                            AsyncDataPublisher asyncDataPublisher = new AsyncDataPublisher(serverUrl,
                                    userName,
                                    passWord);
                                asyncDataPublisher.addStreamDefinition(streamDef);


                            eventPublisherConfig.setDataPublisher(asyncDataPublisher);
                            PublisherUtils.getEventPublisherConfigMap().put(key, eventPublisherConfig);
                        }
                    }
                    dataPublisher = eventPublisherConfig.getDataPublisher();

                    dataPublisher.publish(streamDef.getName(), streamDef.getVersion(), metaDataValueList.toArray(), null,
                            eventData.toArray());

                } catch (AgentException e) {
                    log.error("Error occurred while sending the event", e);
                }
            } else {
                try {
                    LoadBalancingDataPublisher dataPublisher = null;
                    if (eventPublisherConfig == null) {
                        synchronized (Publisher.class) {
                            eventPublisherConfig = new EventPublisherConfig();
                            ArrayList<ReceiverGroup> allReceiverGroups = new ArrayList<ReceiverGroup>();
                            ArrayList<String> receiverGroupUrls = DataPublisherUtil.getReceiverGroups(serverUrl);

                            for (String aReceiverGroupURL : receiverGroupUrls) {
                                ArrayList<DataPublisherHolder> dataPublisherHolders = new ArrayList<DataPublisherHolder>();
                                String[] urls = aReceiverGroupURL.split(",");
                                for (String aUrl : urls) {
                                    DataPublisherHolder aNode = new DataPublisherHolder(null, aUrl.trim(), userName,
                                            passWord);
                                    dataPublisherHolders.add(aNode);
                                }
                                ReceiverGroup group = new ReceiverGroup(dataPublisherHolders);
                                allReceiverGroups.add(group);
                            }

                            LoadBalancingDataPublisher loadBalancingDataPublisher = new LoadBalancingDataPublisher(allReceiverGroups);

                            loadBalancingDataPublisher.addStreamDefinition(streamDef);
                            eventPublisherConfig.setLoadBalancingDataPublisher(loadBalancingDataPublisher);
                            PublisherUtils.getEventPublisherConfigMap().put(key, eventPublisherConfig);
                        }
                    }
                    dataPublisher = eventPublisherConfig.getLoadBalancingDataPublisher();

                    dataPublisher.publish(streamDef.getName(), streamDef.getVersion(), metaDataValueList.toArray(), null,
                            eventData.toArray());
                } catch (AgentException e) {
                    log.error("Error occurred while sending the event", e);
                }
            }
        } catch (MalformedStreamDefinitionException e) {
            log.error("Error while creating stream definition object", e);
        }
    }

    public static StreamDefinition getEventStreamDefinition(MediationStatConfig mediationStatConfig, Object[] metaData)
            throws MalformedStreamDefinitionException {
        StreamDefinition eventStreamDefinition = new StreamDefinition("esb_message_flow_statistics_stream", "1.0.0");
        eventStreamDefinition.setNickName("");
        eventStreamDefinition.setDescription("");
        eventStreamDefinition.addMetaData(DASDataPublisherConstants.DAS_HOST, AttributeType.STRING);
        for (int i = 0; i < metaData.length; i++) {
            eventStreamDefinition.addMetaData(metaData[i].toString(), AttributeType.STRING);
        }
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.TIMESTAMP,
                AttributeType.STRING);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.MESSAGE_ID,
                AttributeType.STRING);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.ENTRY_TYPE,
                AttributeType.STRING);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.ENTRY_NAME,
                AttributeType.STRING);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.PROCESS_TIME,
                AttributeType.LONG);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.FAULT_COUNT,
                AttributeType.INT);
        eventStreamDefinition.addPayloadData(MediationDataPublisherConstants.PAYLOAD,
                AttributeType.STRING);
        return eventStreamDefinition;
    }

}
