/*
 * Data HUb Service (DHuS) - For Space data distribution.
 * Copyright (C) 2013,2014,2015,2016 European Space Agency (ESA)
 * Copyright (C) 2013,2014,2015,2016 GAEL Systems
 * Copyright (C) 2013,2014,2015,2016 Serco Spa
 *
 * This file is part of DHuS software sources.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.gael.drb.cortex.topic.sentinel3.jai.operator;

import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.cortex.topic.sentinel3.jai.operator.Common.PixelCorrection;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Integer;
import fr.gael.drb.value.Value;
import fr.gael.drb.value.ValueArray;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import org.apache.log4j.Logger;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This render image factory is dedicated to the preparation of the SLSTR quicklook operator.
 */
public class QuicklookSlstrRIF implements RenderedImageFactory
{
   private static final Logger LOGGER = Logger.getLogger(QuicklookSlstrRIF.class);

   /**
    * Combine the provided bands to produce a natural color RGB composition
    * It revert to thermal gray scale if the data quality for natural color is
    * poor, eg. the product is acquired during the eclipse.
    *
    * @param param The three R/G/B sources images to be "Merged" together
    *              plus 2 thermal bands (s8 and s9) to produce the Quicklook.
    * @param hints Optionally contains destination image layout.
    */
   public RenderedImage create(ParameterBlock param, RenderingHints hints)
   {
      long start = System.currentTimeMillis();
      RenderedImage computed_image;

      DrbImage s5 = (DrbImage)param.getSource(4);
      DrbImage s3 = (DrbImage)param.getSource(2);
      DrbImage s1 = (DrbImage)param.getSource(0);

      PixelCorrection[]pc=(PixelCorrection[])param.getObjectParameter(0);
      PixelCorrection s5_correction = pc!=null?pc[0]:null;
      PixelCorrection s3_correction = pc!=null?pc[1]:null;
      PixelCorrection s1_correction = pc!=null?pc[2]:null;

      short[][]  detectors =  (short[][]) param.getObjectParameter(1);
      double[][] sza       = (double[][]) param.getObjectParameter(2);

      double[] s5solar = (double[]) param.getObjectParameter(3);
      double[] s3solar = (double[]) param.getObjectParameter(4);
      double[] s1solar = (double[]) param.getObjectParameter(5);

      try
      {
         validateSZA(sza);

         double[][] s3reflectances = radianceToReflectance(s3.getData(),
               s3_correction, detectors, sza, s3solar);
         double[][] s5reflectances = radianceToReflectance(s5.getData(),
               s5_correction, detectors, sza, s5solar);
         double[][] s1reflectances = radianceToReflectance(s1.getData(),
               s1_correction, detectors, sza, s1solar);

         // combine bands as a simple average
         int[][] red=combineAvg(Arrays.asList(s3reflectances, s5reflectances));
         int[][] green= combineAvg(Collections.singletonList(s3reflectances));
         int[][] blue=combineAvg(Arrays.asList(s3reflectances, s1reflectances));

         // build and equalize
         InputStream equalizationLUT = getClass().getClassLoader().
            getResourceAsStream("slstr-equalization.dat");
         computed_image = S3HistogramEqualizer.
            histogramEqualization(red, green, blue, equalizationLUT);
      }
      catch(IllegalStateException e)
      {
         // Image access problem: try to reprocess this other bands
         LOGGER.info(e.getMessage() + ". Using S8 thermal band...");
         DrbImage image = (DrbImage)param.getSource(7); // S8
         PixelCorrection corr = pc!=null?pc[3]:null;
         try
         {
            computed_image = grayScaleBand(image.getData(), corr, true);
         }
         catch (Exception e1)
         {
            // S8 also bad band: try with S9...
            LOGGER.info("Thermal band S8 looks bad. Trying S9...");
            image = (DrbImage)param.getSource(8); // S9
            corr = pc!=null?pc[4]:null;
            try
            {
               computed_image = grayScaleBand(image.getData(), corr, false);
            }
            catch (Exception e2)
            {
               throw new UnsupportedOperationException(
                  "Image cannot be processed (" + e1.getMessage() + ").", e2);
            }
         }
      }

      LOGGER.info("Quicklook generated in " +
         (System.currentTimeMillis() - start)/1000+" secs");

      return computed_image;
   }

   /**
    * Examine the solar zenith angle to detect night products.
    *
    * @param sza solar zenith angles.
    */
   private void validateSZA(double[][] sza)
   {
      for (int j = 0; j < sza.length; j++)
      {
         for (int i = 0; i < sza[0].length; i++)
         {
            if(sza[j][i] > 90.5)
            {
               throw new IllegalStateException("Night product");
            }
         }
      }
   }

   /**
    * convert radiance to top of atmosphere reflectance
    *
    * @param band       the raw band data
    * @param pc         pixel correction
    * @param detectors  detector matrix
    * @param sza        sun zenith angle
    * @param solar_flux solar flux
    * @return TOA reflectance defined in [0, 1]
    */
   private double[][] radianceToReflectance(Raster band, PixelCorrection pc,
         short[][] detectors, double[][] sza, double[] solar_flux)
   {
      int width = band.getWidth();
      int height = band.getHeight();
      double[][] out = new double[width][height];

      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            int detector = detectors[i][j];
            if (detector >= 0 && detector <= 3)
            {
               try
               {
                  if (band.getSample(j, i, 0) == pc.nodata)
                  {
                     // consider no data as bright to cope with cloud saturation
                     out[j][i] = 1.;
                     continue;
                  }

                  double angle = sza[Math.min(i / 2, sza.length - 1)][j / 32];
                  double radiance = band.getSample(j, i, 0) * pc.scale + pc.offset;
                  double ln = radiance / solar_flux[detector];
                  double reflectance = Math.PI * ln / Math.cos(Math.toRadians(angle));

                  out[j][i] = Math.min(1., Math.max(0., reflectance));
               }
               catch (Exception e)
               {
                  LOGGER.error("Unable to convert radiance to TOA reflectance due to "
                        + e.getMessage());
                  throw new IllegalStateException(
                        "Unable to convert radiance to TOA reflectance", e);
               }
            }
         }
      }
      return out;
   }

   /**
    * Combine bands using a simple average method
    *
    * @param bands the bands to combine converted in reflectance
    * @return the resulting band
    */
   private int[][] combineAvg(List<double[][]> bands)
   {
      if (bands == null || bands.isEmpty())
      {
         throw new IllegalArgumentException("invalid argument for band combination");
      }

      int width = bands.get(0).length;
      int height = bands.get(0)[0].length;
      String bandSizes = "";
      for (double[][] r: bands)
      {
         bandSizes += " w: " + r.length + " h:" + r[0].length;
         if (r.length != width || r[0].length != height)
         {
            LOGGER.warn("Provided bands have different size! " + bandSizes);
            width = Math.min(width, r.length);
            height = Math.min(height, r[0].length);
            // workaround: have a reduced size quicklook if input is wrong
         }
      }

      int[][] out = new int[width][height];

      for (int j = 0; j < height; j++)
      {
         for (int i = 0; i < width; i++)
         {
            for (int b = 0; b < bands.size(); b++)
            {
               try
               {
                  double sample = bands.get(b)[i][j];
                  int pixel;
                  // handle no data as bright pixel to avoid saturation over clouds
                  if (Double.isNaN(sample))
                  {
                     pixel = Common.colorRange;
                  }
                  else
                  {
                     pixel = (int) (sample * Common.colorRange);
                  }

                  out[i][j] = (out[i][j] + pixel) / (b == 0 ? 1 : 2);
               }
               catch (Exception e)
               {
                  throw new IllegalArgumentException(
                        "Unable to combine bands during quicklook generation: "
                        + e.getMessage(), e);
               }
            }
         }
      }

      return out;
   }

   public static short[][] extractDetectors(DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode) (image.getItemSource()));

         Query query_rows_number = new Query(
               "indices_an.nc/root/dimensions/rows");
         Query query_cols_number = new Query(
               "indices_an.nc/root/dimensions/columns");
         Query query_data = new Query(
               "indices_an.nc/root/dataset/detector_an/rows/columns");

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
                     ((fr.gael.drb.value.UnsignedByte) values.getElement(index_cols).getValue()).shortValue();
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("detector extraction failure.", e);
         return null;
      }
   }

   public static double[][] extractSZA(DrbCollectionImage sources)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode) (image.getItemSource()));

         Query query_rows_number = new Query(
               "geometry_tn.nc/root/dimensions/rows");
         Query query_cols_number = new Query(
               "geometry_tn.nc/root/dimensions/columns");
         Query query_data = new Query(
               "geometry_tn.nc/root/dataset/solar_zenith_tn/rows/columns");

         Value vrows = query_rows_number.evaluate(node).getItem(0).getValue().
               convertTo(Value.INTEGER_ID);
         Value vcols = query_cols_number.evaluate(node).getItem(0).getValue().
               convertTo(Value.INTEGER_ID);

         int rows = ((Integer) vrows).intValue();
         int cols = ((Integer) vcols).intValue();

         DrbSequence sequence = query_data.evaluate(node);
         double[][] ds = new double[rows][cols];
         for (int index_rows = 0; index_rows < sequence.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode) sequence.getItem(index_rows);
            ValueArray values = (ValueArray) row_node.getValue();
            for (int index_cols = 0; index_cols < values.getLength(); index_cols++)
            {
               ds[index_rows][index_cols] =
                     ((fr.gael.drb.value.Double) values.getElement(index_cols).getValue()).doubleValue();
            }
         }
         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("SZA extraction failure.", e);
         return null;
      }
   }

   public static double[] extractSolarIrradiance(DrbCollectionImage sources, String band)
   {
      try
      {
         DrbImage image = sources.getChildren().iterator().next();
         DrbNode node = ((DrbNode) (image.getItemSource()));

         Query query_col_number = new Query(band
               + "_quality_an.nc/root/dimensions/detectors");
         Query query_data = new Query(band + "_quality_an.nc/root/dataset/"
               + band + "_solar_irradiance_an/detectors");

         Value vcols = query_col_number.evaluate(node).getItem(0).getValue().
               convertTo(Value.INTEGER_ID);

         int cols = ((Integer) vcols).intValue();

         DrbSequence sequence = query_data.evaluate(node);
         double[] ds = new double[cols];
         for (int index_rows = 0; index_rows < sequence.getLength(); index_rows++)
         {
            DrbNode row_node = (DrbNode) sequence.getItem(index_rows);
            ValueArray values = (ValueArray) row_node.getValue();
            for (int index_cols = 0; index_cols < values.getLength(); index_cols++)
            {
               ds[index_cols] =
                     ((fr.gael.drb.value.Double) values.getElement(index_cols).getValue()).doubleValue();
            }
         }

         return ds;
      }
      catch (Exception e)
      {
         LOGGER.error("Solar irradiance extraction failure", e);
         return null;
      }
   }

   private BufferedImage toGrayScale(Raster in, PixelCorrection c,
      boolean invert_color, boolean ignore_bad_stats)
   {
      int width = in.getWidth();
      int height = in.getHeight();
      // compute statistics
      SummaryStatistics stats = new SummaryStatistics();
      for (int j = 0; j < height; j++)
      {
         for (int i = 0; i < width; i++)
         {
            int pixel = checkAndApplyCorrection(in.getSample(i, j, 0), c);
            if (pixel != c.nodata)
            {
               stats.addValue(pixel);
            }
         }
      }

      double lowerBound = Math.max(
         stats.getMin(), 
         stats.getMean() - 2.5*stats.getStandardDeviation());
      double upperBound = Math.min(
         stats.getMax(), 
         stats.getMean() + 2.5*stats.getStandardDeviation());

      if (!ignore_bad_stats)
      {
         if (Double.isNaN(stats.getMean())
               || Double.isNaN(stats.getStandardDeviation())
               || stats.getStandardDeviation() < 2.5 || stats.getMean() < 1.)
         {
            throw new IllegalStateException("Ugly band stats. Acquired during night?");
         }
      }

      return toGrayScale(in, c, invert_color, lowerBound, upperBound);
  }

   private BufferedImage toGrayScale (Raster in, PixelCorrection c,
      boolean invert_color, double lower_bound, double upper_bound)
   {
      double offset = - lower_bound;
      double scaleFactor = 256. / (upper_bound - lower_bound);
      int width =  in.getWidth();
      int height = in.getHeight();

      // generate
      BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

      for (int j = 0; j < height; j++)
      {
         for (int i = 0; i < width; i++)
         {
            int pixel = checkAndApplyCorrection(in.getSample(i, j, 0), c);
            if (pixel == c.nodata)
            {
               // to avoid saturation over clouds it is better to always consider nodata as white
               out.setRGB(i, j, Color.white.getRGB());
               continue;
            }

           double normalized = (pixel + offset)*scaleFactor;
           int gray = (int)(Math.max(0, Math.min(255, normalized)));
            if (invert_color)
            {
               gray = 255 - gray;
            }
           out.setRGB(i, j, new Color(gray, gray, gray).getRGB());
         }
      }
      return out;
  }

   private int checkAndApplyCorrection (int pixel, PixelCorrection c)
   {
      float p = (float)pixel;
      // No correction to apply
      if (c==null) return pixel;
      // NODATA ???
      if (pixel == c.nodata) return c.nodata;
      if (pixel<0) pixel+=(2*(Short.MAX_VALUE+1)); // value never read!

      return (int) (p*c.scale+c.offset); // why cast to int? it shall be double
   }

   private RenderedImage grayScaleBand(Raster band, PixelCorrection c, boolean ignore_bad_stats)
   {
      return toGrayScale(band, c, true, ignore_bad_stats);
   }

}
