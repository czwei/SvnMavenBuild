package com.example.subversionMavenIncrement.ui;

import com.example.subversionMavenIncrement.service.ChoiceActionService;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI实现类
 *
 * @author 蚕豆的生活
 */
public class SvnToolWindow {
    private JPanel panel;
    private JTable table;
    private JScrollPane scrollPane;

    MyDefaultTableModel dataModel = new MyDefaultTableModel();

    private Project project;

    private DataContext dataContext;

    /**
     * 初始化
     */
    public SvnToolWindow() {

    }

    /**
     * 开始任务
     */
    public void okTaskStart(){
        // 选中需要打包的数据
        List<String> list = new ArrayList<String>();
        for(int i=0;i<table.getRowCount();i++){
            if((Boolean)table.getValueAt(i, 0)){
                list.add((String)table.getValueAt(i,1));
            }
        }

        // 使用 IntelliJ 原生进度条，支持取消
        // 使用 invokeLater 延迟调度，确保模态对话框完全关闭后再显示进度条
        ApplicationManager.getApplication().invokeLater(() -> {
            new Task.Backgroundable(project, "生成增量更新包", true) {
                @Override
                public void run(ProgressIndicator indicator) {
                    ChoiceActionService.backEndClasses(project, dataContext, list, indicator);
                }
            }.queue();
        });
    }

    /**
     * 初始化表单数据
     *
     * @param list
     * @param project
     * @param dataContext
     */
    public void currentDateTime(List<String> list, Project project, DataContext dataContext) {

        this.project = project;
        this.dataContext = dataContext;

        dataModel.setRowData(list);
        table.setModel(dataModel);

        // 设置列头宽度
        TableColumn column = table.getColumnModel().getColumn(0);
        column.setPreferredWidth(120);
        column.setMaxWidth(120);
        column.setMinWidth(120);
    }

    public JPanel getContent() {
        return panel;
    }
}
