package javassist;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author 84215
 */
public class LogTransformerByNewMethod implements ClassFileTransformer {
    /**
     * 被处理的包路径
     */
    private final List<String> packagePaths;
    private String proxyEndsWith = "$raw";
    private long timeout;

    public LogTransformerByNewMethod(String[] packagePaths,long timeout) {
        this.packagePaths = new LinkedList<>(Arrays.asList(packagePaths));
        this.timeout = timeout;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String fullClassName = className.replace("/", ".");
        if(!isNeedProxyClass(fullClassName)){
            return null;
        }

//        System.out.println("begin loader="+loader+", className="+fullClassName);
        try {
            CtClass ctclass = ClassPool.getDefault().getCached(fullClassName);
            if(ctclass == null){
                ctclass = ClassPool.getDefault().makeClass(new ByteArrayInputStream(classfileBuffer),false);// 使用全称,用于取得字节码类<使用javassist>
            }

            for (CtMethod oldMethod : ctclass.getDeclaredMethods()) {
                boolean isProxyMethod = isNeedProxyMethod(oldMethod);
                if(!isProxyMethod){
                    break;
                }

                String methodName = oldMethod.getName();
                String newMethodName = methodName + proxyEndsWith;// 新定义一个方法叫做比如sayHello$old


                // 创建新的方法，复制原来的方法，名字为原来的名字
                CtMethod newMethod = CtNewMethod.copy(oldMethod, methodName, ctclass, null);

                String returnStr = "void".equals(newMethod.getReturnType().getName())? "" : "return ";
                String argsStr = newMethod.getParameterTypes() == null || newMethod.getParameterTypes().length == 0? "":"$$";
                StringJoiner parameterStr = new StringJoiner(",");
                CtClass[] parameterTypes = newMethod.getParameterTypes();
                for(CtClass type : parameterTypes){
                    parameterStr.add(type.getSimpleName());
                }

                String finallyMethod = "";
                if(!"main".equals(methodName)){
                    finallyMethod =
                        "long time = System.currentTimeMillis() - startTime;" +
                        "System.out.println(\"cost is \"+time+\"\");"+
                        "if(time > "+timeout+"){"+
                            "throw new RuntimeException(\"" + methodName + "("+parameterStr+") execute cost:\" +(time) +\"ms.\");"+
                        "}";
                }


                // 构建新的方法体
                String bodyStr =
                "{" +
                    "long startTime = System.currentTimeMillis();" +
                    "try{" +
                        returnStr + newMethodName + "("+argsStr+");" +// 调用原有代码，类似于method();($$)表示所有的参数
                    "}finally{" +
                        finallyMethod +
                    "}"+
                "}";

                oldMethod.setName(newMethodName);// 将原来的方法名字修改
                newMethod.setBody(bodyStr);// 替换新方法
                ctclass.addMethod(newMethod);// 增加新方法
            }
            return ctclass.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }finally {
//            System.out.println("end loader="+loader+", className="+fullClassName);
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
        if(method.getName().endsWith(proxyEndsWith)){
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

