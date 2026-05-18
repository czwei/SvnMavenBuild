package com.example.subversionMavenIncrement;

import com.example.subversionMavenIncrement.service.CheckUpService;
import com.example.subversionMavenIncrement.service.ChoiceActionService;
import com.example.subversionMavenIncrement.ui.SvnFormDialog;
import com.example.subversionMavenIncrement.util.NotifyUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * 菜单选择
 *
 * @author 蚕豆的生活
 */
public class ChoiceAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        DataContext dataContext = e.getDataContext();
        Project project = e.getProject();

        // 先初始化数据
        CheckUpService.init(project, dataContext);

        if(CheckUpService.RESOLVE_ADDRESS != null && !CheckUpService.RESOLVE_ADDRESS.isEmpty()) {
            try {
                // 带进度条加载 SVN 提交记录，避免阻塞 EDT
                List<String> svnNotes = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> {
                        try {
                            return ChoiceActionService.findSvnNotes(dataContext);
                        } catch (IOException | InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    },
                    "正在加载 SVN 提交记录...", true, project);

                SvnFormDialog svnFormDialog = new SvnFormDialog(project, svnNotes, dataContext);
                //是否允许用户通过拖拽的方式扩大或缩小你的表单框，我这里定义为true，表示允许
                svnFormDialog.setResizable(true);
                if(svnFormDialog.showAndGet()){
                    svnFormDialog.okTaskStart();
                }
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof IOException || ex.getCause() instanceof InterruptedException) {
                    NotifyUtil.notifyError(project, "出错了！请求SVN错误" + Messages.getInformationIcon());
                }
                throw ex;
            }
        }
    }
}
