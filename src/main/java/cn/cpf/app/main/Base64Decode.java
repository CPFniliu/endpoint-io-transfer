package cn.cpf.app.main;

import cn.cpf.app.util.LogUtils;
import com.github.cosycode.common.helper.CommandLineHelper;

import java.io.*;
import java.nio.file.FileSystemException;
import java.util.Base64;

/**
 * <b>Description : </b> 将Base64编码文件转换为文件
 * <p>
 * <b>created in </b> 2020/11/13
 *
 * @author CPF
 * @since 1.0
 **/
public class Base64Decode {

    /**
     * 将Base64编码文件转换为文件
     *
     * @param args -f 待转换的Base64编码文件路径
     *             -d 转换文件的保存位置
     * @throws IOException 文件不存在或读取失败
     */
    @SuppressWarnings("java:S106")
    public static void main(String[] args) throws IOException {
        CommandLineHelper.requireNonEmpty(args, "参数不能为空");
        final CommandLineHelper lineArgs = CommandLineHelper.parse(args);
        final String filePath = lineArgs.getParam("f");
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在 ==> " + filePath);
        }
        if (!file.isFile()) {
            throw new FileSystemException("不是文件 ==> " + filePath);
        }
        final String savePath = lineArgs.getDefaultParam("d", file.getParentFile().getPath() + File.separator + "base64.zip");
        try (FileReader reader = new FileReader(file)) {
            char[] chars = new char[(int) file.length()];
            final int read = reader.read(chars);
            LogUtils.printDebug("读取文件长度: {}", read);
            String base64 = new String(chars);
            byte[] bytes = Base64.getDecoder().decode(base64);
            writeFile(savePath, bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeFile(String savePath, byte[] content) {
        final File file = new File(savePath);
        try (final FileOutputStream writer = new FileOutputStream(file)) {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
