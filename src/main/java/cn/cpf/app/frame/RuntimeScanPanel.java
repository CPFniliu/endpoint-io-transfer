package cn.cpf.app.frame;

import cn.cpf.app.global.CompContext;
import cn.cpf.app.global.ConfigHelper;
import cn.cpf.app.inter.OnceClickAction;
import cn.cpf.app.util.BdmpOutUtils;
import cn.cpf.app.util.OsUtils;
import com.github.cosycode.bdmp.BdmpHeader;
import com.github.cosycode.common.base.SupplierWithThrow;
import com.github.cosycode.common.ext.bean.DoubleBean;
import com.github.cosycode.common.ext.bean.Record;
import com.github.cosycode.common.ext.hub.LazySingleton;
import com.github.cosycode.common.ext.hub.SimpleCode;
import com.github.cosycode.common.thread.AsynchronousProcessor;
import com.github.cosycode.common.thread.CtrlLoopThreadComp;
import com.github.cosycode.common.util.io.FileSystemUtils;
import com.github.cosycode.ext.swing.comp.JPathTextField;
import com.github.cosycode.ext.swing.comp.StandardGrid;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Supplier;

/**
 * <b>Description : </b> 实时扫描面板
 *
 * @author CPF
 * Date: 2020/8/11 12:14
 */
@Slf4j
public class RuntimeScanPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    @Getter
    private final JPanel boardPanel;

    @Getter
    private final JButton btnOpen;

    @Getter
    private final JButton btnConvert;

    private JCheckBox checkBoxSaveScreenshot;

    private JCheckBox checkBoxOverwrite;

    /**
     * 只作为单线程使用
     */
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss-SSS");

    @Getter
    private final JTextField outPath = new JPathTextField(ConfigHelper.getScanSaveDirPath());

    private final transient LazySingleton<StandardGrid<Record>> tableLazySingleton = LazySingleton.of(() -> {
        final StandardGrid<Record> standardTable = new StandardGrid<>();
        CompContext.CompBean<JComponent> jButtonCompBean = new CompContext.CompBean<JComponent>() {
            @Override
            public void check() {
                ((StandardGrid<?>.StandardTableModel)standardTable.getTableModel()).setOrderHeaderName(getLangString("scanner.table.order"));
                standardTable.setColumnConfigList(Arrays.asList(
                        new StandardGrid.ColumnConfig("tag", getLangString("scanner.table.tag")),
                        new StandardGrid.ColumnConfig("length", getLangString("scanner.table.length")),
                        new StandardGrid.ColumnConfig("deTime", getLangString("scanner.table.deTime"))
                ));
            }
        };
        jButtonCompBean.check();
        CompContext.register(jButtonCompBean);
        standardTable.setPreferredScrollableViewportRowSize(10);
        return standardTable;
    });

    public final transient AsynchronousProcessor<BufferedImage> processor = AsynchronousProcessor.ofConsumer(this::distinguish).setName("异步图片识别线程");

    /**
     * 对截取的屏幕图片对象进行处理
     * <p>对图片进行分析, 如果发现图片中的bdmp信息, 则对其进行解析, 存储文件夹, 并在表格上显示识别到的信息</p>
     *
     * @param image 截取的图片对象
     */
    private void distinguish(BufferedImage image) {
        // 从 路径输入框 获取路径, 如果输入框中的路径为空, 或者不是文件夹, 则使用默认路径, 否则按路径创建对应的文件夹,
        final Supplier<String> dealOutPathSupplier = () -> {
            String text = outPath.getText();
            if (StringUtils.isBlank(text)) {
                text = ConfigHelper.getScanSaveDirPath();
                outPath.setText(text);
            } else {
                File file = new File(text);
                if (!file.exists()) {
                    FileSystemUtils.insureFileDirExist(file);
                } else if (!file.isDirectory()) {
                    text = ConfigHelper.getScanSaveDirPath();
                    outPath.setText(text);
                }
            }
            return text;
        };
        final String saveDir = dealOutPathSupplier.get();
        // 如果启用存储图片功能, 则将截屏存入指定文件夹
        if (checkBoxSaveScreenshot.isSelected()) {
            try {
                ImageIO.write(image, "png", new File(saveDir + "/main-screen-shot-" + dateTimeFormatter.format(LocalDateTime.now()) + ".png"));
            } catch (IOException e) {
                log.error("保存截屏失败");
            }
        }
        final boolean selected = checkBoxOverwrite.isSelected();
        final DoubleBean<Boolean, BdmpHeader> booleanBdmpRecInfoDoubleBean = SimpleCode.runtimeException(
                () -> BdmpOutUtils.convertBinPicToType(image, null, saveDir, selected)
        );
        if (booleanBdmpRecInfoDoubleBean == null) {
            return;
        }
        final Boolean o1 = booleanBdmpRecInfoDoubleBean.getO1();
        if (o1 == null || !o1) {
            // 没有写入成功
            return;
        }
        // 写入成功
        Record record = new Record();
        final BdmpHeader bdmpHeader = booleanBdmpRecInfoDoubleBean.getO2();
        record.put("tag", bdmpHeader.getTag());
        record.put("length", bdmpHeader.getContentLength());
        record.put("deTime", new Date());
        record.put("path", saveDir);
        tableLazySingleton.instance().add(record);
    }

    private final transient LazySingleton<Robot> robotSinTon = LazySingleton.of(() -> SimpleCode.runtimeException((SupplierWithThrow<Robot, AWTException>) Robot::new, "创建Robot对象失败"));

    private final transient CtrlLoopThreadComp ctrlLoopThreadComp = CtrlLoopThreadComp.ofRunnable(() -> {
        try {
            final BufferedImage mainScreenShot = OsUtils.getMainScreenShot(robotSinTon.instance());
            processor.add(mainScreenShot);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }).setName("实时截取主屏幕可控线程").setMillisecond(1000);

    /**
     * Create the panel.
     */
    public RuntimeScanPanel() {
        setBorder(new EmptyBorder(5, 5, 5, 5));
        setLayout(new BorderLayout(0, 0));

        // 透明
        this.setBackground(null);
        this.setOpaque(false);

        btnOpen = CompContext.registerComponent("SelectOutputFolder", new JButton());
        btnOpen.addActionListener(e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            jfc.showOpenDialog(btnOpen);
            File f = jfc.getSelectedFile();
            if (f != null && f.isDirectory()) {
                String path = f.toString();
                outPath.setText(path);
            }
        });

        btnConvert = CompContext.registerComponent("OpenOutputFolder", new JButton());
        btnConvert.addActionListener(e -> {
            String pathText = outPath.getText();
            if (StringUtils.isBlank(pathText)) {
                return;
            }
            File file = new File(pathText);
            if (!file.exists() && !file.isDirectory()) {
                log.warn("文件夹不存在, 请检查路径是否正确");
                return;
            }
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException ex) {
                log.warn("打开输出文件夹异常, 请检查路径是否正确");
                ex.printStackTrace();
            }
        });

        boardPanel = new JPanel();
        boardPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        boardPanel.setLayout(new BorderLayout(0, 0));
        boardPanel.setBackground(null);
        boardPanel.setOpaque(false);
        boardPanel.add(btnConvert, BorderLayout.EAST);
        boardPanel.add(btnOpen, BorderLayout.WEST);
        boardPanel.add(outPath, BorderLayout.CENTER);

        final JComponent toolBar = getToolBar();

        add(boardPanel, BorderLayout.NORTH);
        add(tableLazySingleton.instance(), BorderLayout.CENTER);
        add(toolBar, BorderLayout.SOUTH);
    }

    private JComponent getToolBar() {
        JButton startOrWakeButton = CompContext.registerComponent("RealTimeScanning", new JButton());
        startOrWakeButton.addActionListener(OnceClickAction.of(e -> {
            processor.startOrWake();
            ctrlLoopThreadComp.startOrWake();
        }));
        JButton singleScanButton = CompContext.registerComponent("SingleScan", new JButton());

        singleScanButton.addActionListener(OnceClickAction.of(e -> {
            try {
                final BufferedImage mainScreenShot = OsUtils.getMainScreenShot(robotSinTon.instance());
                distinguish(mainScreenShot);
            } catch (AWTException awtException) {
                awtException.printStackTrace();
            }
        }));
        JButton pauseScanBtn = CompContext.registerComponent("PauseScanning", new JButton());
        pauseScanBtn.addActionListener(OnceClickAction.of(e -> {
            ctrlLoopThreadComp.pause();
        }));
        checkBoxSaveScreenshot = CompContext.registerComponent("SaveScreenshot", new JCheckBox());
        checkBoxOverwrite = CompContext.registerComponent("cover", new JCheckBox());
        JToolBar toolBar = new JToolBar();
        toolBar.add(singleScanButton, 0);
        toolBar.add(startOrWakeButton, 1);
        toolBar.add(pauseScanBtn, 2);
        toolBar.add(checkBoxSaveScreenshot, 3);
        toolBar.add(checkBoxOverwrite, 4);
        return toolBar;
    }

}
