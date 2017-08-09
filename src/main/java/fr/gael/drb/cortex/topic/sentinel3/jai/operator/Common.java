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
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Float;
import fr.gael.drb.value.Short;
import fr.gael.drb.value.Value;
import fr.gael.drbx.image.DrbCollectionImage;
import fr.gael.drbx.image.DrbImage;
import org.apache.log4j.Logger;

import javax.media.jai.RenderedImageAdapter;
import java.awt.*;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Common class gather the OLCI/SLSTR commons implementations and variables.
 */
public class Common
{
   private static Logger LOGGER = Logger.getLogger(QuicklookOlciRIF.class);
   public static final int colorRange = 2048 * 3;

   /**
    * Extracts per bands, the pixel corrections to be applied. Pixel correction
    * is defined by a scale and an offset to apply as followed:
    * <pre>
    * {@code
    *    pixel = scale*extracted + offset;
    * }
    * </pre>
    * As Drb extracts raw data from netcdf Array. The data corrections are not
    * applied. The following code retrieve pixels informations, letting netcdf
    * library apply all the necessary computations:
    * <pre>
    * {@code
    * NetcdfDataset ds=NetcdfDataset.openDataset("Oa04_radiance.nc");
    * VariableEnhanced data = ds.getVariables("Oa04_radiance");
    * ArrayFloat.D2 dataArray = data.read();
    * }
    * </pre>
    * @param sources the sentinel-3 OLCI datasources.
    * @param band where retrieve the corrections settings(starting from 1).
    * @return the correction data structure.
    * @see PixelCorrection
    */
   public static PixelCorrection extractPixelCorrection (
           DrbCollectionImage sources, int band)
   {
      String name=String.format("Oa%1$02d_radiance", band);

      return extractPixelCorrection (sources, name);
   }

   public static PixelCorrection extractPixelCorrection (
           DrbCollectionImage sources, String name)
   {
      DrbImage image = sources.getChildren().iterator().next();
      DrbNode node = ((DrbNode)(image.getItemSource()));

      return extractPixelCorrection (node, name);
   }

   public static PixelCorrection extractPixelCorrection (
           DrbCollectionImage sources, String name, String variableName)
   {
      DrbImage image = sources.getChildren().iterator().next();
      DrbNode node = ((DrbNode)(image.getItemSource()));

      return extractPixelCorrection (node, name, variableName);
   }

   public static PixelCorrection extractPixelCorrection (
           DrbNode product_node, String name)
   {
      return extractPixelCorrection(product_node, name, name);
   }

   public static PixelCorrection extractPixelCorrection (
           DrbNode product_node, String name, String variableName)
   {
      String path = "*[name()='" + name  + ".nc']/root/variables/*[name()='" +
              variableName + "']/attributes/";
      try
      {
         Query query_pixel_scale  = new Query(path + "scale_factor");
         Query query_pixel_offset = new Query(path + "add_offset");
         Query query_nodata = new Query(path + "_FillValue");

         Value vscale = null;
         try
         {
            vscale = query_pixel_scale.evaluate(product_node).getItem(0).
                    getValue().convertTo(Value.FLOAT_ID);
         }
         catch (Exception e)
         {
            vscale = new Float(1);
         }
         Value voffset =null;
         try
         {
            voffset = query_pixel_offset.evaluate(product_node).getItem(0).
                    getValue().convertTo(Value.FLOAT_ID);
         }
         catch (Exception e)
         {
            voffset = new Float(0);
         }
         Value vnodata=null;
         try
         {
            vnodata = query_nodata.evaluate(product_node).getItem(0).
                    getValue().convertTo(Value.SHORT_ID);
         }
         catch (Exception e)
         {
            vnodata = new Short(0);
         }

         float scale  = ((Float)vscale).floatValue();
         float offset = ((Float)voffset).floatValue();
         short nodata = ((Short)vnodata).shortValue();

         return new PixelCorrection(scale, offset, nodata);
      }
      catch (Exception e)
      {
         LOGGER.error("Pixel correction extraction failure.", e);
      }
      return null;
   }

   /**
    * Pixel correction contains elements to apply a correction to a pixel.
    */
   public static class PixelCorrection
   {
      public PixelCorrection(float scale, float offset, int nodata)
      {
         this.scale = scale;
         this.offset = offset;
         this.nodata = nodata;
      }
      public float scale;
      public float offset;
      public int nodata;
   }

   /**
    * Determine if the pass is ascending or descending
    *
    * @param source the
    * @return
    */
   public static boolean isAscending(Object source)
   {
      Object wrapped = source;

      if (source instanceof RenderedImageAdapter)
      {
         wrapped = ((RenderedImageAdapter) source).getWrappedImage();
      }

      if (wrapped instanceof DrbCollectionImage)
      {
         DrbNode node = (DrbNode) ((DrbCollectionImage) wrapped).getItemSource();

         Query q = new Query("data(xfdumanifest.xml/XFDU/metadataSection/"
                 + "metadataObject[@ID=\"measurementOrbitReference\"]/metadataWrap/"
                 + "xmlData/orbitReference/orbitNumber/@groundTrackDirection)");

         DrbSequence seq = q.evaluate(node);

         if ((seq == null) || (seq.getLength() < 1))
         {
            LOGGER.error("Cannot extract ASC/DESC pass from the product");
            return false; // do nothing
         }

         String mode = seq.getItem(0).getValue().toString();
         if (mode == null)
         {
            LOGGER.error("Ascending/Descending mode not found.");
            return false;
         }

         if (mode.toLowerCase().equals("ascending"))
         {
            return true;
         }
         else if (mode.toLowerCase().equals("descending"))
         {
            return false;
         }
         else
         {
            LOGGER.error("Unknown mode : " + mode);
         }
      }
      LOGGER.error("Pass mode cannot be extracted");
      return false;
   }

   public static Color colorMap(float value, TreeMap<java.lang.Float, Color> colorMap)
   {
      Map.Entry<java.lang.Float, Color> enH = colorMap.higherEntry(value);
      Map.Entry<java.lang.Float, Color> enL = colorMap.lowerEntry(value);
      if(enH == null)
         return enL.getValue();
      if(enL == null)
         return enH.getValue();

      float coefficient = (value - enL.getKey())/(enH.getKey() - enL.getKey());
      int red = (int) (enL.getValue().getRed() + coefficient * (enH.getValue().getRed() - enL.getValue().getRed()));
      int green = (int) (enL.getValue().getGreen() + coefficient * (enH.getValue().getGreen() - enL.getValue().getGreen()));
      int blue = (int) (enL.getValue().getBlue() + coefficient * (enH.getValue().getBlue() - enL.getValue().getBlue()));
      return new Color(red, green, blue);
   }

   public static TreeMap<java.lang.Float, Color> loadColormap(String mapName, double min, double max)
   {
      TreeMap<java.lang.Float, Color> colorMap = new TreeMap<>();
      float minValue = java.lang.Float.MAX_VALUE;
      float maxValue = java.lang.Float.MIN_VALUE;
      try
      {
         Properties propMap = new Properties();
         propMap.load(Common.class.getClassLoader().getResourceAsStream(mapName));
         int num = java.lang.Integer.valueOf(propMap.getProperty("numPoints"));

         for(int i = 0; i < num; i++)
         {
            String t[] = propMap.getProperty("color"+i).split(",\\s*");
            float key = java.lang.Float.valueOf(propMap.getProperty("sample"+i));
            colorMap.put(key, new Color(java.lang.Integer.valueOf(t[0]), java.lang.Integer.valueOf(t[1]),
                    java.lang.Integer.valueOf(t[2])));

            if(key < minValue)
               minValue = key;
            if(key > maxValue)
               maxValue = key;
         }
      }
      catch (IOException e)
      {
         LOGGER.error("Unable to load color map. Using gray scale");
         colorMap = new TreeMap<>();
         colorMap.put(0f, Color.BLACK);
         colorMap.put(1f, Color.WHITE);
      }

      double scale = (max - min) / (maxValue - minValue);
      TreeMap<java.lang.Float, Color> colorMapOut = new TreeMap<>();

      for(java.lang.Float key : colorMap.keySet())
         colorMapOut.put((float) (key*scale), colorMap.get(key));

      return colorMapOut;
   }

}
