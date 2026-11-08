package com.pluchon.meng.common.core.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageReader;

// 按长边上限解码：超出时在解码阶段整数倍降采样，内存占用只和上限有关，与原图尺寸无关
public final class BoundedImageReader {

    private BoundedImageReader() {}

    // 读取第一帧，长边不超过 maxEdge；未超出时按原尺寸解码，不损失画质
    public static BufferedImage read(ImageReader reader, int maxEdge) throws IOException {
        int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
        var parameters = reader.getDefaultReadParam();
        int sample = Math.max(1, (longest + maxEdge - 1) / maxEdge);
        parameters.setSourceSubsampling(sample, sample, 0, 0);
        return reader.read(0, parameters);
    }
}
