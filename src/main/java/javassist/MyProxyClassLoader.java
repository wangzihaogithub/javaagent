package javassist;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author 84215
 */
public class MyProxyClassLoader extends Loader {
    /**
     * 被处理的包路径
     */
    private final List<String> packagePaths;
    private long timeout;
    private ClassPool classPool;

    public MyProxyClassLoader(String[] packagePaths, long timeout, ClassPool classPool) {
        this.packagePaths = new LinkedList<>(Arrays.asList(packagePaths));
        this.timeout = timeout;
        this.classPool = classPool;
    }

    @Override
    protected Class findClass(String name) throws ClassNotFoundException {
        byte[] classfile;
        try {
            String jarname =  "/" + name.replace('.', '/') + ".class";
            InputStream in = getClass().getResourceAsStream(jarname);
            if (in == null) {
                return null;
            }
            byte[] oldClassfile = ClassPoolTail.readStream(in);
            byte[] newClassfile = getByteCode(oldClassfile,name);
            if(newClassfile == null){
                classfile = oldClassfile;
            }else {
                classfile = newClassfile;
            }
        }
        catch (Exception e) {
            throw new ClassNotFoundException("caught an exception while obtaining a class file for "+ name, e);
        }

        int i = name.lastIndexOf('.');
        if (i != -1) {
            String pname = name.substring(0, i);
            if (getPackage(pname) == null) {
                try {
                    definePackage(
                            pname, null, null, null, null, null, null, null);
                }
                catch (IllegalArgumentException e) {
                    // ignore.  maybe the package object for the same
                    // name has been created just right away.
                }
            }
        }
        return defineClass(name, classfile, 0, classfile.length);
    }

    public byte[] getByteCode(byte[] bytes, String fullClassName) {
        if(!isNeedProxyClass(fullClassName)){
            return null;
        }
        try {
            CtClass ctclass = classPool.getCached(fullClassName);
            if(ctclass == null){
                ctclass = classPool.makeClass(new ByteArrayInputStream(bytes), false);// 使用全称,用于取得字节码类<使用javassist>
            }
            if(ctclass.isInterface()){
                ctclass.detach();
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
            return ctclass.toBytecode();
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
