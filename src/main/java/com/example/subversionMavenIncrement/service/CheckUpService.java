package com.example.subversionMavenIncrement.service;

import com.example.subversionMavenIncrement.ui.ComAddressFormDialog;
import com.example.subversionMavenIncrement.util.NotifyUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;

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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 加载xml文件
            Document dc = builder.parse(xml);

            // 获取根节点
            Element e = dc.getDocumentElement();
            // 读取 resolve_address - path
            Element resolveAddress = (Element) e.getElementsByTagName("resolve_address").item(0);
            Element path = (Element) resolveAddress.getElementsByTagName("path").item(0);
            RESOLVE_ADDRESS = path.getTextContent();
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

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 加载xml文件
            Document dc = builder.parse(pomFile);

            // 获取根节点
            Element e = dc.getDocumentElement();

            // 遍历根元素下的所有子元素
            NodeList nodeList = e.getElementsByTagName("modules");

            return nodeList.getLength() > 0;

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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 创建document对象
            Document document = builder.newDocument();
            // 创建根节点
            Element projectRoot = document.createElement("project");
            document.appendChild(projectRoot);
            // 向根节点中添加resolve_address子节点
            Element resolveAddress = document.createElement("resolve_address");
            projectRoot.appendChild(resolveAddress);

            // 向resolve_address节点中添加path子节点
            Element path = document.createElement("path");
            // 向子节点设置文本内容
            path.setTextContent(pathText);
            resolveAddress.appendChild(path);

            // 生成xml文件
            File file = new File(filePath);
            if (file.exists()){
                file.delete();
            }

            // 使用Transformer输出格式化的XML
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(new FileOutputStream(file));
            transformer.transform(source, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
