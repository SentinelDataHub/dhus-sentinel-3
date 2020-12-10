package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Integer;
import fr.gael.drb.value.Value;
import fr.gael.drb.value.ValueArray;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;

/**
 * Created by Immedia
 */
public class QuicklookSlstrFRPRIF implements RenderedImageFactory
{
    private static final Logger LOGGER = Logger.getLogger(QuicklookSlstrFRPRIF.class);

    /** Ocean color definition. **/
    private static final int OCEAN_COLOR = new Color(0, 153, 204).getRGB();
    /** Land color definition. **/
    private static final int LAND_COLOR = new Color(195, 195, 136).getRGB();
    /** Fire dot color. */
    private static final Color FIRE_DOT_COLOR  = Color.RED;
    /** Fire dot size.(pixels) */
    private static final int FIRE_DOT_SIZE = 10;
    
    @Override
    public RenderedImage create(ParameterBlock param, RenderingHints renderingHints)
    {
        int[][] flags = (int[][]) param.getObjectParameter(0);
        ArrayList<Point> fires = (ArrayList<Point>) param.getObjectParameter(1);

        RenderedImage raw = (RenderedImage) param.getSource(0);

        int width = (raw).getData().getWidth();
        int height = (raw).getData().getHeight();

        double[][] lst = new double[width][height];

        for (int i = 0; i < height; i++)
        {
            for (int j = 0; j < width; j++)
            {
                
                lst[j][i] = raw.getData().getSample(j, i, 0);
            }
        }

        BufferedImage out = new BufferedImage(lst.length, lst[0].length, BufferedImage.TYPE_3BYTE_BGR);
        
        for (int i = 0; i < height; i++)
        {
            for (int j = 0; j < width; j++)
            {
                if (isLand(flags[i][j]))
                    out.setRGB(j, i, LAND_COLOR);
                else
                    out.setRGB(j, i, OCEAN_COLOR);
            }
        }
        
        if (fires != null && !fires.isEmpty())
        {
            Graphics2D g2d = out.createGraphics();
            g2d.setColor(FIRE_DOT_COLOR);
            for (Point pt : fires)
            {
                g2d.fillOval(pt.x, pt.y, FIRE_DOT_SIZE, FIRE_DOT_SIZE);
            }
            g2d.dispose();
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

            LOGGER.debug("Flags rows:" + rows + "  cols: " + cols);

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

    public static ArrayList<Point> extractFires(DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode) (image.getItemSource()));

         Query query_i_data = new Query(
               "FRP_in.nc/root/dataset/i/fires");
         Query query_j_data = new Query(
                "FRP_in.nc/root/dataset/j/fires");

         DrbSequence sequence_x = query_i_data.evaluate(node);
         DrbSequence sequence_y = query_j_data.evaluate(node);

         LOGGER.debug("Sequence X length:" + sequence_x.getLength());
         ArrayList<Point> ds = new ArrayList<>();
         for (int index_rows = 0; index_rows < sequence_x.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode) sequence_x.getItem(index_rows);
            DrbNode col_node = (DrbNode) sequence_y.getItem(index_rows);
            ValueArray values_x = (ValueArray) row_node.getValue();
            ValueArray values_y = (ValueArray) col_node.getValue();
            LOGGER.debug("Values X length:" + values_x.getLength());
            for (int index_cols = 0; index_cols < values_x.getLength(); index_cols++)
            {
                Point pt = new Point();
                pt.x = ((fr.gael.drb.value.Short) values_x.getElement(index_cols).getValue()).intValue();
                pt.y = ((fr.gael.drb.value.Int) values_y.getElement(index_cols).getValue()).intValue();
                LOGGER.debug ("Pt x:" + pt.x + " y:" + pt.y);
               ds.add(pt);
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("Fires extraction failure.", e);
         return null;
      }
   }

    private boolean isLand(int mask)
    {
        return (mask & 8) > 0;
    }

    private boolean isWater(int mask)
    {
        return (mask & 2) > 0;
    }
}
