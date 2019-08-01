package org.jboss.xavier.integrations.migrationanalytics.business;

import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;
import org.jboss.xavier.analytics.pojo.input.workload.inventory.VMWorkloadInventoryModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VMWorkloadInventoryCalculator implements Calculator<Collection<VMWorkloadInventoryModel>> {
    private static final String VMPATH = "cloudforms.manifest.{version}.vmworkloadinventory.vmPath";
    private static final String CLUSTERPATH = "cloudforms.manifest.{version}.vmworkloadinventory.clusterPath";
    private static final String DATACENTERPATH = "cloudforms.manifest.{version}.vmworkloadinventory.datacenterPath";
    private static final String PROVIDERPATH = "cloudforms.manifest.{version}.vmworkloadinventory.providerPath";
    private static final String GUESTOSFULLNAMEPATH = "cloudforms.manifest.{version}.vmworkloadinventory.guestOSPath";
    private static final String GUESTOSFULLNAME_FALLBACKPATH = "cloudforms.manifest.{version}.vmworkloadinventory.guestOSFallbackPath";
    private static final String VMNAMEPATH = "cloudforms.manifest.{version}.vmworkloadinventory.vmNamePath";
    private static final String NUMCPUPATH = "cloudforms.manifest.{version}.vmworkloadinventory.numCpuPath";
    private static final String HASRDMDISKPATH = "cloudforms.manifest.{version}.vmworkloadinventory.hasRDMDiskPath";
    private static final String RAMSIZEINBYTES = "cloudforms.manifest.{version}.vmworkloadinventory.ramSizeInBytesPath";
    private static final String NICSPATH = "cloudforms.manifest.{version}.vmworkloadinventory.nicsPath";
    private static final String PRODUCTNAMEPATH = "cloudforms.manifest.{version}.vmworkloadinventory.productNamePath";
    private static final String DISKSIZEPATH = "cloudforms.manifest.{version}.vmworkloadinventory.diskSizePath";
    private static final String EMSCLUSTERIDPATH = "cloudforms.manifest.{version}.vmworkloadinventory.emsClusterIdPath";

    @Autowired
    private Environment env;
    
    private String cloudFormsJson;
    private String manifestVersion;

    @Override
    public Collection<VMWorkloadInventoryModel> calculate(String cloudFormsJson, Map<String, Object> headers) {
        manifestVersion = getManifestVersion(cloudFormsJson);
        this.cloudFormsJson = cloudFormsJson;

        List<Map> vmList = readListValuesFromExpandedEnvVarPath(VMPATH, null);
        return vmList.stream().map(e -> createVMWorkloadInventoryModel(e)).collect(Collectors.toList());
    }

    private VMWorkloadInventoryModel createVMWorkloadInventoryModel(Map vmStructMap) {
        VMWorkloadInventoryModel model = new VMWorkloadInventoryModel();
        model.setProvider(readValueFromExpandedEnvVarPath(PROVIDERPATH, vmStructMap));
        
        vmStructMap.put("ems_cluster_id", readValueFromExpandedEnvVarPath(EMSCLUSTERIDPATH, vmStructMap));
        model.setDatacenter(readValueFromExpandedEnvVarPath(DATACENTERPATH, vmStructMap));
        
        model.setCluster(readValueFromExpandedEnvVarPath(CLUSTERPATH, vmStructMap));
        
        model.setVmName(readValueFromExpandedEnvVarPath(VMNAMEPATH, vmStructMap ));
        model.setMemory(readValueFromExpandedEnvVarPath(RAMSIZEINBYTES, vmStructMap));
        model.setCpuCores(readValueFromExpandedEnvVarPath(NUMCPUPATH, vmStructMap));
        model.setOsProductName(readValueFromExpandedEnvVarPath(PRODUCTNAMEPATH, vmStructMap));
        model.setGuestOSFullName(StringUtils.defaultIfEmpty(readValueFromExpandedEnvVarPath(GUESTOSFULLNAMEPATH, vmStructMap ), readValueFromExpandedEnvVarPath(GUESTOSFULLNAME_FALLBACKPATH, vmStructMap )));
        model.setHasRdmDisk(readValueFromExpandedEnvVarPath(HASRDMDISKPATH, vmStructMap));

        List<Number> diskSpaceList = readListValuesFromExpandedEnvVarPath(DISKSIZEPATH, vmStructMap);
        model.setDiskSpace(diskSpaceList.stream().filter(e -> e != null).mapToLong(Number::longValue).sum());

        model.setNicsCount(readValueFromExpandedEnvVarPath(NICSPATH, vmStructMap));
        
        return model;
    }

    private <T> T readValueFromExpandedEnvVarPath(String envVarPath, Map vmStructMap) {
        String envVarPathWithExpandedVersion = expandVersionInExpression(envVarPath);
        String path = env.getProperty(envVarPathWithExpandedVersion);
        String expandParamsInPath = expandParamsInPath(path, vmStructMap);

        Object value = JsonPath.parse(cloudFormsJson).read(expandParamsInPath);
        if (value instanceof Collection) {
            return ((List<T>) value).get(0); 
        } else {
            return (T) value;
        }    
    }
        
    private <T> List<T> readListValuesFromExpandedEnvVarPath(String envVarPath, Map vmStructMap) {
        String envVarPathWithExpandedVersion = expandVersionInExpression(envVarPath);
        String path = env.getProperty(envVarPathWithExpandedVersion);
        String expandParamsInPath = expandParamsInPath(path, vmStructMap);

        Object value = JsonPath.parse(cloudFormsJson).read(expandParamsInPath);
        if (value instanceof Collection) {
            return ((List<T>) value); 
        } else {
            return Collections.singletonList((T) value);
        }    
    }
    
    private String expandVersionInExpression(String path) {
        String replace = path.replace("{version}", manifestVersion);
        return replace;
    }
    
    private String expandParamsInPath(String path, Map vmStructMap) {
        Pattern p = Pattern.compile("\\{[a-zA-Z1-9_]+\\}");
        Matcher m = p.matcher(path);
        while (m.find() && vmStructMap != null) {
            String key = m.group().substring(1, m.group().length() - 1);
            String value = vmStructMap.containsKey(key) ? vmStructMap.get(key).toString() : "";
            path = path.replace(m.group(), value);
        }

        return path;
    }
}