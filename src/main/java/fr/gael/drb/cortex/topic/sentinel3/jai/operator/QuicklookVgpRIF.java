package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import fr.gael.drbx.image.DrbImage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;

/**
 * Created by fmarino on 31/05/2017.
 */
public class QuicklookVgpRIF implements RenderedImageFactory
{
    /**
     * This operator could be called by chunks of images.
     * it uses the following composition
     * red: MIR
     * green: 0.5 * (B2 + B3)
     * blue: B0 + 0.1 * MIR
     * @param param The three R/G/B sources images to be "Merged" together
     *              to produce the Quicklook.
     * @param hints Optionally contains destination image layout.
     */
    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints hints)
    {
        DrbImage b3 = (DrbImage)param.getSource(0);
        DrbImage b2 = (DrbImage)param.getSource(1);
        DrbImage b0 = (DrbImage)param.getSource(2);
        DrbImage mir = (DrbImage)param.getSource(3);

        Common.PixelCorrection[]pc=(Common.PixelCorrection[])param.getObjectParameter(0);
        Common.PixelCorrection mirc = pc[3];
        Common.PixelCorrection b0c = pc[2];
        Common.PixelCorrection b2c = pc[1];
        Common.PixelCorrection b3c = pc[0];

        if(mirc == null || b0c == null || b2c == null || b3c == null)
            throw new IllegalArgumentException("Pixel corrections can't be null");

        int width = b0.getData().getWidth();
        int height = b0.getData().getHeight();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                if (b0.getData().getSample(j, i, 0) != b0c.nodata &&
                        b2.getData().getSample(j, i, 0) != b2c.nodata &&
                        b3.getData().getSample(j, i, 0) != b3c.nodata &&
                        mir.getData().getSample(j, i, 0) != mirc.nodata)
                {
                    double b0Corrected = b0.getData().getSample(j, i, 0) * b0c.scale + b0c.offset;
                    double b2Corrected = b2.getData().getSample(j, i, 0) * b2c.scale + b2c.offset;
                    double b3Corrected = b3.getData().getSample(j, i, 0) * b3c.scale + b3c.offset;
                    double MIRCorrected = mir.getData().getSample(j, i, 0) * mirc.scale + mirc.offset;
                    Color pixel = new Color(
                            (int) (MIRCorrected*255),
                            (int) (0.5*255*(b2Corrected + b3Corrected)),
                            (int) ((b0Corrected + 0.1*MIRCorrected)*255)
                    );
                    out.setRGB(j, i, pixel.getRGB());
                }

        return out;
    }
}
