/*
 * Copyright (c) 2012. Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package com.yammer.metrics.reporting.jmx;

import com.yammer.metrics.annotation.AnnotatedMetric;
import com.yammer.metrics.annotation.AnnotatedMetricAttribute;
import com.yammer.metrics.annotation.Publish;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class JmxMetric implements DynamicMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmxMetric.class);
    private final Metric object;
    private final ObjectName name;
    private final MBeanInfo beanInfo;
    private final Map<String, PublishedAttribute> metricAttributes = new HashMap<String, PublishedAttribute>();
    private final MetadataMBean metadataMBean;

    public JmxMetric(String registryName, MetricName metricName, Metric obj) throws Exception {
        if (obj == null) {
            throw new IllegalArgumentException("object cannot be null");
        }

        object = obj;
        name = createObjectName(registryName, metricName, "value");
        AnnotatedMetric annotatedMetric = new AnnotatedMetric(object);

        List<AnnotatedMetricAttribute> annotatedMetricAttrs = annotatedMetric.getAttributes();
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[annotatedMetricAttrs.size()];

        for (int i = 0; i < annotatedMetricAttrs.size(); ++i) {
            AnnotatedMetricAttribute annotatedMetricAttribute = annotatedMetricAttrs.get(i);
            PublishedAttribute attr = new PublishedAttribute(metricName, annotatedMetricAttribute);
            Publish m = attr.getAnnotation();
            metricAttributes.put(annotatedMetricAttribute.getName(), attr);
            attributes[i] = attr.getValueAttributeInfo();
        }

        beanInfo = new MBeanInfo(
                object.getClass().getCanonicalName(),
                "Metric MBean",
                attributes,  // attributes
                null,  // constructors
                null,  // operations
                null); // notifications

        ObjectName metadataName = createObjectName(registryName, metricName, "metadata");

        MBeanInfo metadataInfo = new MBeanInfo(
                object.getClass().getCanonicalName(),
                "Metric Metdata MBean",
                attributes,  // attributes
                null,  // constructors
                null,  // operations
                null); // notifications
        metadataMBean = new MetadataMBean(metadataName, metadataInfo, metricAttributes);
    }

    private ObjectName createObjectName(String registryName, MetricName metricName, String field) {
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append((registryName == null) ? "DefaultMetricRegistry" : registryName)
                .append(":type=")
                .append(metricName.getType());

        addIfNotNull(nameBuilder, "group", metricName.getGroup());
        addIfNotNull(nameBuilder, "name", metricName.getName());
        addIfNotNull(nameBuilder, "scope", metricName.getScope());
        addIfNotNull(nameBuilder, "feild", field);

        String name = nameBuilder.toString();
        try {
            return new ObjectName(nameBuilder.toString());
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("invalid ObjectName " + name, e);
        }
    }
    
    private void addIfNotNull(StringBuilder builder, String key, String value){
        if(value!=null) builder.append(String.format(",%s=", key)).append(value);
    }

    public ObjectName getObjectName() {
        return name;
    }

    public MetadataMBean getMetadataMBean() {
        return metadataMBean;
    }

    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException {
        PublishedAttribute attr = metricAttributes.get(attribute);
        if (attr == null) {
            throw new AttributeNotFoundException(attribute);
        }
        try {
            return attr.getValue();
        } catch (Exception e) {
            throw new MBeanException(e);
        }
    }

    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        for (String a : attributes) {
            try {
                list.add(new Attribute(a, getAttribute(a)));
            } catch (Exception e) {
                LOGGER.warn("getAttribute() failed for " + a, e);
            }
        }
        return list;
    }

    public MBeanInfo getMBeanInfo() {
        return beanInfo;
    }

    public Object invoke(
            String actionName, Object[] params, String[] signature) {
        throw new UnsupportedOperationException(
                "invoke(...) is not supported on this mbean");
    }

    public void setAttribute(Attribute attribute) {
        throw new UnsupportedOperationException(
                "setAttribute(...) is not supported on this mbean");
    }

    public AttributeList setAttributes(AttributeList attributes) {
        throw new UnsupportedOperationException(
                "setAttributes(...) is not supported on this mbean");
    }
}
