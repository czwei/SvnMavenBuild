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
     * Windows 上通过 chcp 获取控制台活动代码页（与 SVN 命令行输出编码一致），
     * 避免 sun.jnu.encoding / native.encoding 在 JBR 中被覆写为 UTF-8 导致中文乱码
     */
    public static final String CHARSET = getNativeCharset();

    /**
     * 获取操作系统原生编码
     * Windows：通过 chcp 命令获取控制台代码页（cmd /c 执行命令时使用的编码）
     * 非 Windows：使用 native.encoding → sun.jnu.encoding → UTF-8 回退链
     */
    private static String getNativeCharset() {
        if (isWindows()) {
            String codePage = detectWindowsConsoleCodePage();
            if (codePage != null) {
                return codePage;
            }
        }
        // JDK 18+ 提供的原生编码属性
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

    /**
     * 通过 chcp 命令获取 Windows 控制台活动代码页
     * 例如中文 Windows 返回 CP936（GBK），英文 Windows 返回 CP437 或 CP1252
     *
     * @return Java 可识别的 charset 名称，如 "CP936"；检测失败返回 null
     */
    private static String detectWindowsConsoleCodePage() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "chcp"});
            byte[] bytes = IOUtils.toByteArray(p.getInputStream());
            // 从 chcp 输出中提取代码页数字（如 "活动代码页: 936" → 936）
            // ASCII 数字在所有编码中都是单字节，无需知道输出编码即可提取
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                if (b >= '0' && b <= '9') {
                    sb.append((char) b);
                }
            }
            if (sb.length() > 0) {
                return "CP" + sb.toString();
            }
        } catch (Exception ignored) {
            // 检测失败，回退到默认方式
        }
        return null;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toUpperCase().contains("WINDOWS");
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
