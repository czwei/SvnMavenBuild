package com.example.subversionMavenIncrement.service;

import com.example.subversionMavenIncrement.run.ExecuteCommand;
import com.example.subversionMavenIncrement.ui.FailPathFormDialog;
import com.example.subversionMavenIncrement.util.FileUtil;
import com.example.subversionMavenIncrement.util.NotifyUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.history.VcsFileRevision;

import javax.swing.*;
import java.awt.*;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 逻辑处理
 *
 * @author 蚕豆的生活
 */
public class ChoiceActionService {

    private static Pattern pattern;

    private static final String CLASSES = File.separator + "classes";

    private static final String[] PACKAGING_FOLDER = {"resources", "src"};

    /**
     * 获取svn提交的历史记录
     *
     * @param dataContext
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static List<String> findSvnNotes(DataContext dataContext) throws IOException, InterruptedException {

        // 获取选中的svn相关数据 VCS_FILE_REVISIONS -> {VcsFileRevision[4]@54552} VCS_REVISION_NUMBERS -> {VcsRevisionNumber[7]@50905}
        VcsFileRevision[] vcsFileRevisions = com.intellij.openapi.vcs.VcsDataKeys.VCS_FILE_REVISIONS.getData(dataContext);

        Set<String> svnRecords = new HashSet<>();

        for (VcsFileRevision vcs : vcsFileRevisions) {
            // 获取svn提交的历史记录
            String s = ExecuteCommand.carrOut(null, "svn log",
                    vcs.getChangedRepositoryPath().toPresentableString(),
                    "-r", vcs.getRevisionNumber().asString(),
                    "-v");
            List<String> strings = svnDataHandling(s, " [M|A] /(.*?)\r\n");
            svnRecords.addAll(strings);
        }

        return new ArrayList<>(svnRecords);
    }

    /**
     * 生成更新包后续处理 classes文件夹获取
     *
     * @param project
     * @param dataContext
     * @param svnSubmitData
     */
    public static void backEndClasses(Project project, DataContext dataContext, List<String> svnSubmitData, ProgressIndicator indicator) {

        // 先创建临时时文件夹
        String temporaryFile = CheckUpService.RESOLVE_ADDRESS + File.separator + "target";

        String dist = temporaryFile + File.separator + "dist";

        FileUtil.deleteDirectoryStream(dist);

        // 检查 target/classes 是否存在编译文件
        File classesDir = new File(temporaryFile + CLASSES);
        if (!classesDir.exists() || !classesDir.isDirectory()) {
            NotifyUtil.notifyError(project, classesDir.getAbsolutePath() + " 目录不存在，请先编译项目（mvn compile 或 IDE Build）" + Messages.getInformationIcon());
            return;
        }

        // 打包失败的文件路径
        List<String> failPaths = new ArrayList<>();

        // 初始化进度条
        indicator.setIndeterminate(false);
        int total = svnSubmitData.size();
        int index = 0;

        try {
            // 只移动 resources 和 src
            for (String path : svnSubmitData) {

                // 检查用户是否取消
                indicator.checkCanceled();

                // 更新进度
                indicator.setFraction((double) index / total);
                indicator.setText("处理中(" + (index + 1) + "/" + total + "): " + path);

                for (String fold : PACKAGING_FOLDER) {
                    String[] paths = path.split(fold);

                    if(fold.equals(PACKAGING_FOLDER[1]) && path.contains("/" + PACKAGING_FOLDER[0])) {
                        continue;
                    }

                    if (paths.length > 1 && path.contains(fold) && path.contains(".")) {

                        paths[1] = paths[1].replace(".java", ".class");
                        processingSvnPath(fold, paths);

                        try {
                            if (!new File(dist + CLASSES + paths[1]).exists()) {
                                Path parent = Path.of(temporaryFile + CLASSES + paths[1]).getParent();
                                String pathParent = Path.of(dist + CLASSES + paths[1]).getParent().toString();
                                Files.createDirectories(Path.of(dist + CLASSES + paths[1]).getParent());
                                Files.copy(Path.of(temporaryFile + CLASSES + paths[1]),
                                        Path.of(dist + CLASSES + paths[1]));

                                // 查找子类文件也加入打包中 只争对.class文件
                                subClass(paths, parent, pathParent);

                            }
                        } catch (Exception e) {
                            failPaths.add(path + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
                        }
                    }
                }

                index++;
            }

            // 完成时将进度设为100%
            indicator.setFraction(1.0);
            indicator.setText("打包完成，正在生成结果...");

            packagingComp(project, dist, failPaths);
        } catch (ProcessCanceledException e) {
            // 用户取消打包，不弹窗提示
        } catch (IOException e) {
            NotifyUtil.notifyError(project, "生成更新包失败！请查看 " + temporaryFile + CLASSES + " 文件夹是否有文件" + Messages.getInformationIcon());
            throw new RuntimeException(e);
        }
    }

    /**
     * 打包完成提示
     *
     * @param project
     * @param dist
     * @param failPaths
     * @throws IOException
     */
    private static void packagingComp(Project project, String dist, List<String> failPaths) throws IOException {
        // 生成完后打开文件夹
        try {
            Desktop.getDesktop().open(new File(dist));
        } catch (Exception e) {
            // 沙箱环境中可能无法打开文件夹，忽略异常
        }

        if (failPaths.size() == 0) {
            NotifyUtil.notifyInfo(project, "打包成功,已打开打包好的文件夹，或者请查看 target/dist 文件夹");
        } else {
            // 不是主线程执行ui操作，使用 SwingUtilities.invokeLater() 方法
            SwingUtilities.invokeLater(() -> {
                FailPathFormDialog failPathFormDialog = new FailPathFormDialog(failPaths, project);
                failPathFormDialog.setResizable(true);
                failPathFormDialog.show();
            });
        }
    }

    /**
     * SVN 数据处理分割
     *
     * @param data
     * @param canonical
     * @return
     */
    private static List<String> svnDataHandling(String data, String canonical) {

        List<String> list = new ArrayList<>();

        Pattern pattern = Pattern.compile(canonical);
        Matcher matcher = pattern.matcher(data);
        while (matcher.find()) {
            //截取出括号中的内容
            String substring = matcher.group(1);

            if (substring.contains("(")) {
                substring = substring.replaceAll("\\s", "");
                substring = substring.split("\\(")[0];
            }

            list.add(substring);
        }
        return list;
    }

    /**
     * 查找子类文件也加入打包中 只争对.class文件
     *
     * @param paths
     * @param parent
     * @param pathParent
     * @throws IOException
     */
    private static void subClass(String[] paths, Path parent, String pathParent) throws IOException {
        // 查找子类文件也加入打包中 只争对.class文件
        String s1 = Path.of(paths[1]).getFileName().toString();
        if (s1.contains(".class")) {
            String pathsFileName = s1.replace(".class", "");
            Files.list(parent)
                    .filter(Files::isRegularFile)
                    .forEach(subPath -> {
                        String s = subPath.getFileName().toString();
                        if (s.contains(".class")) {
                            String replace = s.replace(".class", "");
                            if (replace.contains(pathsFileName)
                                    && !replace.equals(pathsFileName)) {
                                try {
                                    Files.copy(subPath, Path.of(pathParent + File.separator + s));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
        }
    }

    /**
     * 处理svn路径
     *
     * @param fold
     * @param paths
     */
    private static void processingSvnPath(String fold, String[] paths) {

        if (PACKAGING_FOLDER[1].equals(fold)) {
            String substring = paths[1].substring(0, 10);
            if ("/main/java".equals(substring)) {
                paths[1] = paths[1].substring(10);
            }
        }

    }

}
