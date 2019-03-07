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
 * Created by s3team on 6/5/17.
 */
public class QuicklookSlstrL2LRIF implements RenderedImageFactory
{
    private static final Logger LOGGER = Logger.getLogger(QuicklookSlstrL2LRIF.class);

    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints renderingHints)
    {
        Common.PixelCorrection pc = ((Common.PixelCorrection[])param.getObjectParameter(0))[0];

        int[][] flags = (int[][]) param.getObjectParameter(1);
        int[][] exceptions = (int[][]) param.getObjectParameter(2);

        RenderedImage raw = (RenderedImage) param.getSource(0);

        int width = (raw).getData().getWidth();
        int height = (raw).getData().getHeight();

        double[][] lst = new double[width][height];

        // sanity checks on input data
        if(pc == null)
            throw new IllegalArgumentException("Pixel corrections can't be null");

        BufferedImage out = new BufferedImage(lst.length, lst[0].length, BufferedImage.TYPE_3BYTE_BGR);
        TreeMap<Float, Color> colorMap = Common.loadColormap("lst.cpd",
                263,
                321);

        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
            {
                if (raw.getData().getSample(j, i, 0) != pc.nodata)
                {
                    Color t = Common.colorMap((float) raw.getData().getSample(j, i, 0) * pc.scale + pc.offset, colorMap);
                    out.getRaster().setPixel(j, i, new int[]{t.getRed(), t.getGreen(), t.getBlue()});
                }
                else if (isUnfilled(flags[i][j]))
                    out.getRaster().setPixel(j, i, new int[]{0, 0, 0});
                else if (isSnowIce(flags[i][j]))
                    out.getRaster().setPixel(j, i, new int[]{185, 253, 255});
                else if (isWater(flags[i][j]))
                    out.getRaster().setPixel(j, i, new int[]{0, 0, 0});
                else if (lstUnderflow(exceptions[i][j]))
                    out.getRaster().setPixel(j, i, new int[]{0, 0, 171});
                else if (lstOverflow(exceptions[i][j]))
                    out.getRaster().setPixel(j, i, new int[]{170, 0, 0});
                else
                    out.getRaster().setPixel(j, i, new int[]{255, 255, 255});

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
                    "flags_in.nc/root/dimensions/rows");
            Query query_cols_number = new Query(
                    "flags_in.nc/root/dimensions/columns");
            Query query_data = new Query(
                    "flags_in.nc/root/dataset/confidence_in/rows/columns");

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
                            ((fr.gael.drb.value.UnsignedShort) values.getElement(index_cols).getValue()).intValue();
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

    public static int[][] extractExceptions(DrbCollectionImage sources)
    {
        try
        {
            DrbImage image = sources.getChildren().iterator().next();
            DrbNode node = ((DrbNode) (image.getItemSource()));

            Query query_rows_number = new Query(
                    "LST_in.nc/root/dimensions/rows");
            Query query_cols_number = new Query(
                    "LST_in.nc/root/dimensions/columns");
            Query query_data = new Query(
                    "LST_in.nc/root/dataset/exception/rows/columns");

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
                            ((fr.gael.drb.value.Short) values.getElement(index_cols).getValue()).shortValue();
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
        return (mask & 8192) > 0;
    }

    private boolean isCloud(int mask)
    {
        return (mask & 16384) > 0;
    }

    private boolean isLand(int mask)
    {
        return (mask & 8) > 0;
    }

    private boolean isWater(int mask)
    {
        return (mask & 2) > 0;
    }

    private boolean isUnfilled(int mask)
    {
        return (mask & 32) > 0;
    }

    private boolean lstUnderflow(int exceptions)
    {
        return (exceptions & 256) > 0;
    }

    private boolean lstOverflow(int exceptions)
    {
        return (exceptions & 512) > 0;
    }
}
