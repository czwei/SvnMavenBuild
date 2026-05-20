package com.example.subversionMavenIncrement.service;

import com.example.subversionMavenIncrement.ui.ComAddressFormDialog;
import com.example.subversionMavenIncrement.util.NotifyUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * 开始检查 Maven 是否是多模块
 *
 * @author 蚕豆的生活
 */
public class CheckUpService {

    /**
     * 解析包地址
     */
    public static String RESOLVE_ADDRESS = null;

    /**
     * 初始化数据
     *
     * @param project
     * @param dataContext
     */
    public static void init(Project project, DataContext dataContext){

        RESOLVE_ADDRESS = null;

        VirtualFile virtualFile = (VirtualFile) com.intellij.openapi.vcs.VcsDataKeys.VCS_VIRTUAL_FILE.getData(dataContext);
        String vcsPath = virtualFile.getPath();

        // VCS_VIRTUAL_FILE 可能指向子目录（如 src/），向上查找真正的模块根目录
        String moduleRoot = resolveModuleRoot(vcsPath);
        String ideaRoot = findIdeaRoot(moduleRoot);
        if (ideaRoot == null) {
            ideaRoot = moduleRoot;
        }

        CheckUpService checkUpService = new CheckUpService();

        // 先检查是否有 svnMavenIncrement.xml
        File xml = new File(ideaRoot + File.separator + ".idea" + File.separator + "svnMavenIncrement.xml");
        if(xml.exists()) {
            checkUpService.getXml(xml);
        }

        // 读取完 svnMavenIncrement.xml 还是null 就解析项目结构
        if(RESOLVE_ADDRESS != null && !RESOLVE_ADDRESS.isEmpty()) {
            return;
        }

        // 单结构，就是当前目录
        if (!checkUpService.isModules(project, moduleRoot)){
            RESOLVE_ADDRESS = moduleRoot;
            checkUpService.createXml(ideaRoot + File.separator + ".idea" + File.separator + "svnMavenIncrement.xml", moduleRoot);
            return;
        }

        // 多结构需要输入补全
        try {
            ComAddressFormDialog comAddressFormDialog = new ComAddressFormDialog(moduleRoot, project, dataContext, checkUpService);
            comAddressFormDialog.setResizable(true);
            if(comAddressFormDialog.showAndGet()){
                comAddressFormDialog.okTaskStart();
            }else {
                RESOLVE_ADDRESS = null;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    /**
     * 表单提交地址写入 svnMavenIncrement.xml
     *
     * @param project
     * @param dataContext
     * @param path
     */
    public void setXml(Project project, DataContext dataContext, String path) {

        RESOLVE_ADDRESS = path;
        String ideaRoot = findIdeaRoot(path);
        if (ideaRoot == null) {
            ideaRoot = path;
        }
        createXml(ideaRoot + File.separator + ".idea" + File.separator + "svnMavenIncrement.xml", path);
    }

    /**
     * 读取 svnMavenIncrement.xml 中的 path信息
     *
     * @param xml
     */
    private void getXml(File xml) {
        try {
            SAXReader reader = new SAXReader();
            // 加载xml文件
            Document dc= reader.read(xml);

            // 获取根节点
            Element e = dc.getRootElement();
            // 读取 resolve_address - path
            Element path = e.element("resolve_address").element("path");
            RESOLVE_ADDRESS = path.getText();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 检测是否有maven子项目
     *
     * @param project
     * @param path
     * @return
     */
    private boolean isModules(Project project, String path) {

        File pomFile = new File(path + File.separator + "pom.xml");
        if (!pomFile.exists()) {
            return false;
        }

        SAXReader reader = new SAXReader();

        try {
            // 加载xml文件
            Document dc= reader.read(pomFile);

            // 获取根节点
            Element e = dc.getRootElement();

            // 遍历根元素下的所有子元素
            List<Element> eList = e.elements("modules");

            return eList.size() > 0;

        }catch (Exception e){
            NotifyUtil.notifyError(project, "出错了！获取pom.xml出错" + Messages.getInformationIcon());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 向上查找 Maven/Gradle 模块根目录（包含 pom.xml 或 build.gradle）
     */
    private static String resolveModuleRoot(String path) {
        File current = new File(path);
        while (current != null) {
            if (new File(current, "pom.xml").exists()
                    || new File(current, "build.gradle").exists()
                    || new File(current, "build.gradle.kts").exists()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return path;
    }

    /**
     * 向上查找 IntelliJ 项目根目录（包含 .idea 文件夹）
     */
    private static String findIdeaRoot(String path) {
        File current = new File(path);
        while (current != null) {
            if (new File(current, ".idea").isDirectory()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * 写入生成 svnMavenIncrement.xml
     *
     * @param filePath
     * @param pathText
     */
    public void createXml(String filePath, String pathText){
        try {
            // 创建document对象
            Document document = DocumentHelper.createDocument();
            // 创建根节点StudentRoot
            Element studentRoot = document.addElement("project");
            // 向根节点中添加第一个子节点student
            Element student = studentRoot.addElement("resolve_address");

            // 向student节点中添加子节点name和age
            Element path = student.addElement("path");
            // 向子节点设置文本内容
            path.setText(pathText);

            // 设置生成xml的格式
            OutputFormat format = OutputFormat.createPrettyPrint();
            // 设置编码格式
            format.setEncoding("UTF-8");

            // 生成xml文件
            File file = new File(filePath);
            if (file.exists()){
                file.delete();
            }

            // 创建一个xml写入对象
            XMLWriter writer = new XMLWriter(new FileOutputStream(file), format);
            // 设置是否转义，默认使用转义字符
            writer.setEscapeText(false);
            // 把document对象写入到输出流中
            writer.write(document);
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
