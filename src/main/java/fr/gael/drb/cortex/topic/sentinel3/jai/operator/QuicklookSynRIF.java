package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Integer;
import fr.gael.drb.value.Value;
import fr.gael.drb.value.ValueArray;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;


/**
 * Created by fmarino on 08/05/2017.
 */
public class QuicklookSynRIF implements RenderedImageFactory
{
    private static final Logger LOGGER = Logger.getLogger(QuicklookSynRIF.class);

    /**
     * This operator could be called by chunks of images.
     * it uses the same bands as olci, and the same equalization LUT, but it avoids reflectance conversion
     * as it has already been done by ipf
     * @param param The three R/G/B sources images to be "Merged" together
     *              to produce the Quicklook.
     * @param hints Optionally contains destination image layout.
     */
    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints hints)
    {
        Common.PixelCorrection[]pc=(Common.PixelCorrection[])param.getObjectParameter(0);
        Common.PixelCorrection bandCorrection = pc[0];

        short[][] flags = (short[][]) param.getObjectParameter(1);

        RenderedImage band = (RenderedImage) param.getSource(0);

        int width = (band).getData().getWidth();
        int height = (band).getData().getHeight();

        double[][] reflectance = new double[width][height];

        // sanity checks on input data
        if(bandCorrection == null)
            throw new IllegalArgumentException("Pixel corrections can't be null");

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                if (band.getData().getSample(j, i, 0) != bandCorrection.nodata )
                    reflectance[j][i] = band.getData().getSample(j, i, 0) * bandCorrection.scale
                            + bandCorrection.offset;

        BufferedImage out = new BufferedImage(
                reflectance.length, reflectance[0].length, BufferedImage.TYPE_3BYTE_BGR);

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
            {
                if (isInvalid(flags[i][j]))
                    out.setRGB(j, i, Color.BLACK.getRGB());
                else if(isCloud(flags[i][j]))
                    out.setRGB(j, i, new Color(255, 255, 255).getRGB());
                else if (band.getData().getSample(j, i, 0) != bandCorrection.nodata)
                    out.setRGB(j, i, new Color((int)(255*reflectance[j][i]), (int)(255*reflectance[j][i]),
                            (int)(255*reflectance[j][i])).getRGB());
                else if (!isLand(flags[i][j]))
                    out.setRGB(j, i, new Color(52, 58, 144).getRGB());
                else
                    out.setRGB(j, i, Color.WHITE.getRGB());
            }

        return out;
    }

    public static short[][] extractFlags(DrbCollectionImage sources)
    {
        try
        {
            DrbImage image = sources.getChildren().iterator().next();
            DrbNode node = ((DrbNode) (image.getItemSource()));

            Query query_rows_number = new Query(
                    "flags.nc/root/dimensions/rows");
            Query query_cols_number = new Query(
                    "flags.nc/root/dimensions/columns");
            Query query_data = new Query(
                    "flags.nc/root/dataset/SYN_flags/rows/columns");

            Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);
            Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);

            int rows = ((Integer) vrows).intValue();
            int cols = ((Integer) vcols).intValue();

            DrbSequence sequence = query_data.evaluate(node);
            short[][] ds = new short[rows][cols];
            for (int index_rows = 0; index_rows < sequence.getLength(); index_rows++)
            {
                DrbNode row_node = (DrbNode) sequence.getItem(index_rows);
                ValueArray values = (ValueArray) row_node.getValue();
                for (int index_cols = 0; index_cols < values.getLength(); index_cols++)
                {
                    ds[index_rows][index_cols] =
                            ((fr.gael.drb.value.UnsignedShort) values.getElement(index_cols).getValue()).shortValue();
                }
            }
            return ds;
        }
        catch (Exception e)
        {
            LOGGER.error("flags extraction failure.", e);
            return null;
        }
    }

    private boolean isCloud(short mask)
    {
        return (mask & 1) > 0 || (mask & 256) > 1;
    }

    private boolean isLand(short mask)
    {
        return (mask & 16) > 0;
    }

    private boolean isBorder(short mask)
    {
        return (mask & 1024) > 0;
    }

    private boolean isInvalid(short mask)
    {
        return (mask & 32) > 0;
    }
}
