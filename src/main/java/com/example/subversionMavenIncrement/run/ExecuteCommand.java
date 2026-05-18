package com.example.subversionMavenIncrement.run;

import com.example.subversionMavenIncrement.StaticValue;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 执行命令
 *
 * @author 蚕豆的生活
 */
public class ExecuteCommand {

    /**
     * 是以cmd开头还是sh开头
     */
    private static final String COMMAND_HEADER;

    /**
     * /c或者-c
     */
    private static final String HEADER_C;

    /**
     * 获取系统原生编码格式
     * JDK 18+ 使用 native.encoding 获取操作系统原生编码（JEP 400）
     * 低版本 JDK 回退到 sun.jnu.encoding
     */
    public static final String CHARSET = getNativeCharset();

    /**
     * 获取操作系统原生编码
     * 优先使用 native.encoding（JDK 18+），
     * 解决 sun.jnu.encoding 在新版 JDK 中返回 UTF-8 导致 Windows 中文环境 SVN 输出乱码的问题
     */
    private static String getNativeCharset() {
        // JDK 18+ 提供的原生编码属性，能准确反映操作系统编码
        String nativeEncoding = System.getProperty("native.encoding");
        if (nativeEncoding != null && !nativeEncoding.isEmpty()) {
            return nativeEncoding;
        }
        // 旧版 JDK 回退
        String jnuEncoding = System.getProperty("sun.jnu.encoding");
        if (jnuEncoding != null && !jnuEncoding.isEmpty()) {
            return jnuEncoding;
        }
        return "UTF-8";
    }

    static {

        // 初始化参数
        String os = System.getProperty("os.name");
        if (os != null && os.toUpperCase().contains(StaticValue.WINDOWS.toString())) {
            COMMAND_HEADER = "cmd";
            HEADER_C = "/c";
        }else {
            COMMAND_HEADER = "sh";
            HEADER_C = "-c";
        }
    }

    /**
     * 执行命令行命令
     *
     * @param cmdS 命令 传入方式："svn log", "-v"
     * @param file 在什么位置执行命令
     * @return 执行的字符串
     * @throws IOException io异常
     */
    public static String carrOut(File file, String... cmdS) throws IOException, InterruptedException {

        List<String> list = new ArrayList<>();
        Collections.addAll(list, COMMAND_HEADER, HEADER_C);
        list.addAll(List.of(cmdS));

        String[] cmd = list.toArray(String[]::new);

        Runtime runtime = Runtime.getRuntime();
        Process process;
        if(null == file){
            process = runtime.exec(cmd);
            return IOUtils.toString(process.getInputStream(), CHARSET);
        }else {
            process = runtime.exec(cmd, null, file);
            InputStream ers= process.getErrorStream();
            InputStream ins= process.getInputStream();
            new Thread(new inputStreamThread(ins)).start();
            process.waitFor();
            ers.close();
            return null;
        }
    }

    static class inputStreamThread implements Runnable{
        private InputStream ins = null;
        private BufferedReader bfr = null;
        public inputStreamThread(InputStream ins){
            this.ins = ins;
            this.bfr = new BufferedReader(new InputStreamReader(ins));
        }
        @Override
        public void run() {
            String line = null;
            byte[] b = new byte[100];
            int num = 0;
            try {
                while((num=ins.read(b))!=-1){
                    System.out.println(new String(b, 0, num, CHARSET));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
