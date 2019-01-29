package com.rt.agent;

import javassist.ClassPool;
import javassist.LogTransformerByInsertCode;

import java.lang.instrument.Instrumentation;

/**
 * jvm启动参数加 -javaagent:D:\wangzihao\workspace\kf-imes-new\plug\javaagent-1.0.jar -Drt.agent.packages=com.kf.imes.controller
 *
 * @author 84215
 */
public class Javaagent {

    public static void premain(String agentArgs, Instrumentation instrumentation){
        System.out.println("begin agentArgs="+agentArgs+", instrumentation="+instrumentation);

        long timeout = Long.parseLong(System.getProperty("rt.agent.timeout","1000"));
        String packages = System.getProperty("rt.agent.packages","");
        if(packages.isEmpty()){
            return;
        }

        ClassPool classPool = ClassPool.getDefault();

        ClassLoader classLoader = new javassist.MyProxyClassLoader("1".split(","),timeout,classPool);
        Thread.currentThread().setContextClassLoader(classLoader);

        LogTransformerByInsertCode transformer = new LogTransformerByInsertCode(packages.split(","),timeout,classPool);
        instrumentation.addTransformer(transformer);
    }

}
