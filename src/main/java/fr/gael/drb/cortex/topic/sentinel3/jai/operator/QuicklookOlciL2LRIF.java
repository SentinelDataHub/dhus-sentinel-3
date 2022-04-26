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
import java.util.TreeMap;

/**
 * Created by s3team on 6/1/17.
 */
public class QuicklookOlciL2LRIF implements RenderedImageFactory
{
    private static final Logger LOGGER = Logger.getLogger(QuicklookOlciL2LRIF.class);

    /**
     * it uses the GIFAPAR index plus mask to fill in empty areas
     * gifapar index is mapped using a color map
     * @param param the GIFAPAR index
     * @param hints Optionally contains destination image layout.
     */
    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints hints)
    {
        Common.PixelCorrection pc = ((Common.PixelCorrection[])param.getObjectParameter(0))[0];

        int[][] flags = (int[][]) param.getObjectParameter(1);

        RenderedImage raw = (RenderedImage) param.getSource(0);

        int width = raw.getData().getWidth();
        int height = raw.getData().getHeight();

        // named gifapar but also includes ogvi
        double[][] gifapar = new double[width][height];

        // sanity checks on input data
        if(pc == null)
            throw new IllegalArgumentException("Pixel corrections can't be null");

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                if (raw.getData().getSample(j, i, 0) != pc.nodata )
                    gifapar[j][i] = raw.getData().getSample(j, i, 0) * pc.scale + pc.offset;

        BufferedImage out = new BufferedImage(gifapar.length, gifapar[0].length, BufferedImage.TYPE_3BYTE_BGR);
        TreeMap<Float, Color> colorMap = Common.loadColormap("ndvi.cpd",
                -0.1,
                1);

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++) {
                if (isInvalid(flags[i][j]))
                    out.setRGB(j, i, Color.BLACK.getRGB());
                else if(isCloud(flags[i][j]))
                    out.setRGB(j, i, new Color(255, 255, 255).getRGB());
                else if (isSnowIce(flags[i][j]))
                    out.setRGB(j, i, new Color(185, 253, 255).getRGB());
                else if (isWater(flags[i][j]))
                    out.setRGB(j, i, new Color(52, 58, 144).getRGB());
                else if (raw.getData().getSample(j, i, 0) != pc.nodata)
                    out.setRGB(j, i, Common.colorMap((float) gifapar[j][i], colorMap).getRGB());
                else
                    out.setRGB(j, i, Color.WHITE.getRGB());
            }

        return out;
    }

    public static int[][] extractFlags(DrbCollectionImage sources)
    {
        try
        {
            DrbImage image = sources.getChildren().iterator().next();
            DrbNode node = ((DrbNode) (image.getItemSource()));

            Query query_rows_number = new Query(
                    "lqsf.nc/root/dimensions/rows");
            Query query_cols_number = new Query(
                    "lqsf.nc/root/dimensions/columns");
            Query query_data = new Query(
                    "lqsf.nc/root/dataset/LQSF/rows/columns");

            Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);
            Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
                    convertTo(Value.INTEGER_ID);

            int rows = ((Integer) vrows).intValue();
            int cols = ((Integer) vcols).intValue();

            DrbSequence sequence = query_data.evaluate(node);
            int[][] ds = new int[rows][cols];
            for (int index_rows = 0; index_rows < sequence.getLength(); index_rows++)
            {
                DrbNode row_node = (DrbNode) sequence.getItem(index_rows);
                ValueArray values = (ValueArray) row_node.getValue();
                for (int index_cols = 0; index_cols < values.getLength(); index_cols++)
                {
                    ds[index_rows][index_cols] =
                            ((fr.gael.drb.value.Int) values.getElement(index_cols).getValue()).intValue();
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

    private boolean isSnowIce(int mask)
    {
        return (mask & 16) > 0;
    }

    private boolean isCloud(int mask)
    {
        return (mask & 8) > 0;
    }

    private boolean isLand(int mask)
    {
        return (mask & 4) > 0;
    }

    private boolean isWater(int mask)
    {
        return (mask & 2) > 0;
    }

    private boolean isInvalid(int mask)
    {
        return (mask & 1) > 0;
    }
}
