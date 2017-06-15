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

import org.apache.log4j.Logger;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JAI Operator dedicated to compute Sentinel-3 histogram equalization.
 */
public class S3HistogramEqualizer
{

   private static final Logger LOGGER = Logger.getLogger(S3HistogramEqualizer.class);

   /**
    * Adjust image color using an histogram equalization algorithm.
    *
    * @param input_red        red band TOA reflectance.
    * @param input_green      green band TOA reflectance.
    * @param input_blue       blue band TOA reflectance.
    * @param equalizationFile static LUT for qualization.
    *
    * @return the equalized image.
    */
   public static BufferedImage histogramEqualization(
         double[][] input_red, double[][] input_green, double[][] input_blue,
         InputStream equalizationFile)
   {
      int[][] red   = new int[input_red.length][input_red[0].length];
      int[][] green = new int[input_red.length][input_red[0].length];
      int[][] blue  = new int[input_red.length][input_red[0].length];

      for (int j = 0; j < input_red[0].length; j++)
      {
         for (int i = 0; i < input_red.length; i++)
         {
            red[i][j]   = (int) (input_red[i][j]   * Common.colorRange);
            green[i][j] = (int) (input_green[i][j] * Common.colorRange);
            blue[i][j]  = (int) (input_blue[i][j]  * Common.colorRange);
         }
      }
      return histogramEqualization(red, green, blue, equalizationFile);
   }

   /**
    * Adjust image color using an histogram equalization algorithm.
    *
    * @param input_red         matrix of pixel in the range [0, Common.colorRange] for the red band.
    * @param input_green       matrix of pixel in the range [0, Common.colorRange] for the green band.
    * @param input_blue        matrix of pixel in the range [0, Common.colorRange] for the blue band.
    * @param equalization_file if provided, it applies the transformation using a LUT defined in this file.
    *
    * @return the equalized image.
    */
   public static BufferedImage histogramEqualization(
         int[][] input_red, int[][] input_green, int[][] input_blue,
         InputStream equalization_file)
   {
      // Get the Lookup table for histogram equalization
      List<int[]> hist_lut;

      if (equalization_file == null)
      {
         hist_lut = histogramEqualizationLUT(input_red, input_green, input_blue);
      }
      else
      {
         try
         {
            ObjectInputStream objectinputstream = new ObjectInputStream(equalization_file);
            hist_lut = (ArrayList<int[]>) objectinputstream.readObject();
            List<int[]> histLUTDynamic = histogramEqualizationLUT(input_red, input_green, input_blue);

            // average
            for (int b = 0; b < hist_lut.size(); b++)
            {
               for (int i = 0; i < hist_lut.get(b).length; i++)
               {
                  // weighted average between static and dynamic LUT
                  hist_lut.get(b)[i] = (hist_lut.get(b)[i] * 2) / 3 + histLUTDynamic.get(b)[i] / 3;
               }
            }

         }
         catch (Exception e)
         {
            LOGGER.warn(
                  "Unable to load LUT for equalization. Using a dynamic LUT. "
                  + e.getMessage());
            hist_lut = histogramEqualizationLUT(input_red, input_green, input_blue);
         }
      }

      BufferedImage histogramEQ = new BufferedImage(
            input_red.length, input_red[0].length, BufferedImage.TYPE_3BYTE_BGR);

      int red, green, blue, new_pixel;
      for (int i = 0; i < input_red.length; i++)
      {
         for (int j = 0; j < input_red[0].length; j++)
         {
            // Get pixels by R, G, B
            red = input_red[i][j];
            green = input_green[i][j];
            blue = input_blue[i][j];

            // Set new pixel values using the histogram lookup table
            if (red == 0 && green == 0 && blue == 0)
            {
               continue;
            }

            red = hist_lut.get(0)[red];
            green = hist_lut.get(1)[green];
            blue = hist_lut.get(2)[blue];

            new_pixel = new Color(
                  red * 255 / Common.colorRange,
                  green * 255 / Common.colorRange,
                  blue * 255 / Common.colorRange).getRGB();

            // Write pixels into image
            histogramEQ.setRGB(i, j, new_pixel);
         }
      }

      return histogramEQ;
   }

   /**
    * calculate the corrective look up table from the histogram
    *
    * @param input_red   red band
    * @param input_green green band
    * @param input_blue  blue band
    * @return the resulting LUT
    */
   private static List<int[]> histogramEqualizationLUT(int[][] input_red,
         int[][] input_green, int[][] input_blue)
   {
      // Get an image histogram - calculated values by R, G, B channels
      List<int[]> image_hist = imageHistogram(input_red, input_green, input_blue);

      // Create the lookup table
      List<int[]> image_lut = new ArrayList<>();

      // Fill the lookup table
      int[] rhistogram = new int[Common.colorRange + 1];
      int[] ghistogram = new int[Common.colorRange + 1];
      int[] bhistogram = new int[Common.colorRange + 1];

      for (int i = 0; i < rhistogram.length; i++)
      {
         rhistogram[i] = 0;
      }
      for (int i = 0; i < ghistogram.length; i++)
      {
         ghistogram[i] = 0;
      }
      for (int i = 0; i < bhistogram.length; i++)
      {
         bhistogram[i] = 0;
      }

      long sumr = 0;
      long sumg = 0;
      long sumb = 0;

      // Calculate the scale factor
      float scale_factor =
            (float) (Common.colorRange * 1. / (input_red.length * input_red[0].length));

      for (int i = 0; i < rhistogram.length; i++)
      {
         sumr += image_hist.get(0)[i];
         int valr = (int) (sumr * scale_factor);

         rhistogram[i] = valr;

         sumg += image_hist.get(1)[i];
         int valg = (int) (sumg * scale_factor);

         ghistogram[i] = valg;

         sumb += image_hist.get(2)[i];
         int valb = (int) (sumb * scale_factor);

         bhistogram[i] = valb;
      }

      image_lut.add(rhistogram);
      image_lut.add(ghistogram);
      image_lut.add(bhistogram);

      return image_lut;

   }

   /**
    * calculate the image histogram for each RGB band
    *
    * @param input_red
    * @param input_green
    * @param input_blue
    * @return
    */
   private static List<int[]> imageHistogram(
         int[][] input_red, int[][] input_green, int[][] input_blue)
   {
      int[] rhistogram = new int[Common.colorRange + 1];
      int[] ghistogram = new int[Common.colorRange + 1];
      int[] bhistogram = new int[Common.colorRange + 1];

      for (int i = 0; i < rhistogram.length; i++)
      {
         rhistogram[i] = 0;
      }
      for (int i = 0; i < ghistogram.length; i++)
      {
         ghistogram[i] = 0;
      }
      for (int i = 0; i < bhistogram.length; i++)
      {
         bhistogram[i] = 0;
      }
      boolean flag = false;

      for (int i = 0; i < input_red.length; i++)
      {
         for (int j = 0; j < input_red[0].length; j++)
         {
            int red = input_red[i][j];
            int green = input_green[i][j];
            int blue = input_blue[i][j];

            // Increase the values of colors
            if (red == 0 && green == 0 && blue == 0)
            {
               if (!flag)
               {
                  flag = true;
                  continue;
               }
               else
               {
                  flag = false;
               }
            }
            rhistogram[red]++;
            ghistogram[green]++;
            bhistogram[blue]++;
         }
      }

      ArrayList<int[]> hist = new ArrayList<>();
      hist.add(rhistogram);
      hist.add(ghistogram);
      hist.add(bhistogram);

      return hist;
   }

}
