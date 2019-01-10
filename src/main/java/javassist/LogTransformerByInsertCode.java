package javassist;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author 84215
 */
public class LogTransformerByInsertCode implements ClassFileTransformer {
    /**
     * 被处理的包路径
     */
    private final List<String> packagePaths = new LinkedList<>();
    private long timeout;

    public LogTransformerByInsertCode() {
        super();
        String packages = System.getProperty("rt.agent.packages");
        if(packages != null && packages.length() > 0){
            this.packagePaths.addAll(Arrays.asList(packages.split(",")));
        }
        this.timeout = Long.parseLong(System.getProperty("rt.agent.timeout","1000"));
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String fullClassName = className.replace("/", ".");
        if(!isNeedProxyClass(fullClassName)){
            return null;
        }

        ClassPool classPool = ClassPool.getDefault();
        CtClass ctclass = null;
        try {
            ctclass = classPool.getCached(fullClassName);
            if(ctclass == null){
                ctclass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer),false);// 使用全称,用于取得字节码类<使用javassist>
            }
            if(ctclass.isInterface()){
                return null;
            }

            for (CtMethod ctMethod : ctclass.getDeclaredMethods()) {
                boolean isProxyMethod = isNeedProxyMethod(ctMethod);
                if(!isProxyMethod){
                    continue;
                }

                String fullMethodName = ctMethod.getMethodInfo2().toString();


                ctMethod.addLocalVariable("startTime",CtClass.longType);
                ctMethod.insertBefore("startTime = System.currentTimeMillis();" );
                ctMethod.insertAfter("long time = System.currentTimeMillis() - startTime;" +
                        "System.out.println(\"------------------\"+Thread.currentThread()+\"  "+fullMethodName+" runtime cost is \"+time+\"\");"+
                        "if(time > "+timeout+"){"+
                            "throw new RuntimeException(\"" + fullMethodName+") execute cost:\" +(time) +\"ms.\");"+
                        "}");
                System.out.println("代理方法 - " + fullMethodName);
            }
            byte[] bytes = ctclass.toBytecode();
            ctclass.detach();
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void add(String packagePath) {
        packagePaths.add(packagePath);
    }

    private boolean isNeedProxyClass(String className){
        if(packagePaths.isEmpty()){
            return false;
        }
        if(className.contains("$$")){
            return false;
        }

        for(String packagePath : packagePaths){
            if(className.startsWith(packagePath)){
                return true;
            }
        }
        return false;
    }

    private boolean isNeedProxyMethod(CtMethod method) throws ClassNotFoundException {
        String name = method.getName();
        if("main".equals(name)){
            return false;
        }
        for(Object ann : method.getAnnotations()) {
            if(ann.toString().startsWith("@org.aspectj")){
                return false;
            }
        }
        return true;
    }
}

