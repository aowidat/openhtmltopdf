/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Joshua Marinacci
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package org.xhtmlrenderer.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;

import javax.swing.JComponent;

import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.css.style.CssContext;
import org.xhtmlrenderer.layout.FontUtil;
import org.xhtmlrenderer.layout.InlinePaintable;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.layout.PersistentBFC;

public class BlockBox extends Box implements Renderable, InlinePaintable {

    public int renderIndex;
    
    private List pendingInlineElements;
    private MarkerData markerData;
    
    private int listCounter;
    
    private PersistentBFC persistentBFC;
    
    private Box staticEquivalent;
    
    public JComponent component = null;

    public BlockBox() {
        super();
    }

    public void adjustWidthForChild(int childWidth) {
        if (getStyle().isAutoWidth() && childWidth > contentWidth) {
            contentWidth = childWidth;
        }
        if (getParent() != null) {
            getParent().adjustWidthForChild(getWidth());
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("BlockBox:");
        sb.append(super.toString());

        if (getStyle().isFixed()) {
            sb.append(" position: fixed");
        }
        return sb.toString();
    }

    public int getIndex() {
        return renderIndex;
    }

    public double getAbsTop() {
        return getAbsY();
    }

    public double getAbsBottom() {
        return getAbsY() + height;
    }

    public void paintListMarker(RenderingContext c) {
        if (getStyle().isHidden()) {
            return;
        }
        
        if (getStyle().isListItem()) {
            ListItemPainter.paint(c, this);
        }
    }

    public List getPendingInlineElements() {
        return pendingInlineElements;
    }

    public void setPendingInlineElements(List pendingInlineElements) {
        this.pendingInlineElements = pendingInlineElements;
    }
    
    public boolean intersects(CssContext cssCtx, Shape clip) {
        if (! getStyle().isListItem()) {
            return super.intersects(cssCtx, clip);
        } else {
            // HACK Don't know how wide the list marker is (or even where it is)
            // so extend the bounding box all the way over to the left edge of
            // the canvas
            if (clip == null) {
                return true;
            }
            
            Rectangle borderEdge = getBorderEdge(getAbsX(), getAbsY(), cssCtx);
            int delta = borderEdge.x;
            borderEdge.x = 0;
            borderEdge.width += delta;
            
            return clip.intersects(borderEdge);
        }
    }
    
    public void paintInline(RenderingContext c) {
        if (getStyle().isHidden()) {
            return;
        }
        
        getContainingLayer().paintAsLayer(c, this);
    }
    
    public boolean isInline() {
        Box parent = getParent();
        return parent instanceof LineBox || parent instanceof InlineBox;
    }
    
    public void paintDebugOutline(RenderingContext c) {
        paintDebugOutline(c, Color.RED);
    }

    public MarkerData getMarkerData() {
        return markerData;
    }

    public void setMarkerData(MarkerData markerData) {
        this.markerData = markerData;
    }
    
    public void createMarkerData(LayoutContext c, StrutMetrics strutMetrics) {
        MarkerData result = new MarkerData();
        result.setStructMetrics(strutMetrics);
        
        CalculatedStyle style = getStyle().getCalculatedStyle();
        IdentValue listStyle = style.getIdent(CSSName.LIST_STYLE_TYPE);
        
        String image = style.getStringProperty(CSSName.LIST_STYLE_IMAGE);
        if (! image.equals("none")) {
            result.setImageMarker(makeImageMarker(c, strutMetrics, image));
        } else {
            if (listStyle == IdentValue.CIRCLE || listStyle == IdentValue.SQUARE ||
                    listStyle == IdentValue.DISC) {
                result.setGlyphMarker(makeGlyphMarker(strutMetrics));
            } else {
                result.setTextMarker(makeTextMarker(c, listStyle));
            }
        }
        
        setMarkerData(result);
    }
    
    private MarkerData.GlyphMarker makeGlyphMarker(StrutMetrics strutMetrics) {
        int diameter = (int)((strutMetrics.getAscent() + strutMetrics.getDescent()) / 3);
        
        MarkerData.GlyphMarker result = new MarkerData.GlyphMarker();
        result.setDiameter(diameter);
        result.setLayoutWidth(diameter * 3);
       
       return result;
    }
    
    
    private MarkerData.ImageMarker makeImageMarker(
            LayoutContext c, StrutMetrics structMetrics, String image) {
        Image img = null;
        if (! image.equals("none")) {
            img = c.getUac().getImageResource(image).getImage();
            if (img != null) {
                StrutMetrics strutMetrics = structMetrics;
                if (img.getHeight(null) > strutMetrics.getAscent()) {
                    img = img.getScaledInstance(-1, (int)strutMetrics.getAscent(), Image.SCALE_FAST);
                }
                MarkerData.ImageMarker result = new MarkerData.ImageMarker();
                result.setImage(img);
                result.setLayoutWidth(img.getWidth(null) * 2);
                return result;
            }
        }
        return null;
    }
    
    private MarkerData.TextMarker makeTextMarker(LayoutContext c, IdentValue listStyle) {
        String text = "";

        if (listStyle == IdentValue.LOWER_LATIN || listStyle == IdentValue.LOWER_ALPHA) {
            text = toLatin(getListCounter()).toLowerCase() + ".";
        } else if (listStyle == IdentValue.UPPER_LATIN || listStyle == IdentValue.UPPER_ALPHA) {
            text = toLatin(getListCounter()).toUpperCase() + ".";
        } else if (listStyle == IdentValue.LOWER_ROMAN) {
            text = toRoman(getListCounter()).toLowerCase() + ".";
        } else if (listStyle == IdentValue.UPPER_ROMAN) {
            text = toRoman(getListCounter()).toUpperCase() + ".";
        } else if (listStyle == IdentValue.DECIMAL) {
            text = getListCounter() + ".";
        }
        
        text += "  ";

        Font font = this.getStyle().getCalculatedStyle().getAWTFont(c);
        
        int w = FontUtil.len(text, font, c.getTextRenderer(), c.getGraphics());
        
        MarkerData.TextMarker result = new MarkerData.TextMarker();
        result.setText(text);
        result.setLayoutWidth(w);
        result.setFont(font);
        
        return result;
    } 

    private static String toLatin(int val) {
        if (val > 26) {
            int val1 = val % 26;
            int val2 = val / 26;
            return toLatin(val2) + toLatin(val1);
        }
        return ((char) (val + 64)) + "";
    }

    private static String toRoman(int val) {
        int[] ints = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] nums = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < ints.length; i++) {
            int count = (int) (val / ints[i]);
            for (int j = 0; j < count; j++) {
                sb.append(nums[i]);
            }
            val -= ints[i] * count;
        }
        return sb.toString();
    }

    public int getListCounter() {
        return listCounter;
    }

    public void setListCounter(int listCounter) {
        this.listCounter = listCounter;
    }

    public PersistentBFC getPersistentBFC() {
        return persistentBFC;
    }

    public void setPersistentBFC(PersistentBFC persistentBFC) {
        this.persistentBFC = persistentBFC;
    }
    
    public Box getStaticEquivalent() {
        return staticEquivalent;
    }

    public void setStaticEquivalent(Box staticEquivalent) {
        this.staticEquivalent = staticEquivalent;
    }
    
    public void alignToStaticEquivalent() {
        this.y = staticEquivalent.getAbsY() - getAbsY();
        setAbsY(staticEquivalent.getAbsY());
    }

    public boolean isReplaced() {
        return component != null;
    }
}

/*
 * $Id$
 *
 * $Log$
 * Revision 1.29  2005/12/17 02:24:14  peterbrant
 * Remove last pieces of old (now non-working) clip region checking / Push down handful of fields from Box to BlockBox
 *
 * Revision 1.28  2005/12/15 20:04:48  peterbrant
 * Implement visibility: hidden
 *
 * Revision 1.27  2005/12/13 20:46:06  peterbrant
 * Improve list support (implement list-style-position: inside, marker "sticks" to first line box even if there are other block boxes in between, plus other minor fixes) / Experimental support for optionally extending text decorations to box edge vs line edge
 *
 * Revision 1.26  2005/12/13 02:41:33  peterbrant
 * Initial implementation of vertical-align: top/bottom (not done yet) / Minor cleanup and optimization
 *
 * Revision 1.25  2005/12/09 21:41:20  peterbrant
 * Finish support for relative inline layers
 *
 * Revision 1.24  2005/12/09 01:24:56  peterbrant
 * Initial commit of relative inline layers
 *
 * Revision 1.23  2005/12/05 00:13:53  peterbrant
 * Improve list-item support (marker positioning is now correct) / Start support for relative inline layers
 *
 * Revision 1.22  2005/11/25 22:38:39  peterbrant
 * Clean imports
 *
 * Revision 1.21  2005/11/25 16:57:19  peterbrant
 * Initial commit of inline content refactoring
 *
 * Revision 1.20  2005/11/12 21:55:27  tobega
 * Inline enhancements: block box text decorations, correct line-height when it is a number, better first-letter handling
 *
 * Revision 1.19  2005/11/08 22:53:46  tobega
 * added getLineHeight method to CalculatedStyle and hacked in some list-item support
 *
 * Revision 1.18  2005/11/08 20:03:57  peterbrant
 * Further progress on painting order / improved positioning implementation
 *
 * Revision 1.17  2005/11/05 18:45:06  peterbrant
 * General cleanup / Remove obsolete code
 *
 * Revision 1.16  2005/10/27 00:09:02  tobega
 * Sorted out Context into RenderingContext and LayoutContext
 *
 * Revision 1.15  2005/10/21 13:17:15  pdoubleya
 * Rename some methods in RectPropertySet, cleanup.
 *
 * Revision 1.14  2005/10/21 12:01:20  pdoubleya
 * Added cachable rect property for margin, cleanup minor in styling.
 *
 * Revision 1.13  2005/10/21 05:52:10  tobega
 * A little more experimenting with flattened render tree
 *
 * Revision 1.12  2005/10/18 20:57:04  tobega
 * Patch from Peter Brant
 *
 * Revision 1.11  2005/10/16 23:57:16  tobega
 * Starting experiment with flat representation of render tree
 *
 * Revision 1.10  2005/10/06 03:20:22  tobega
 * Prettier incremental rendering. Ran into more trouble than expected and some creepy crawlies and a few pages don't look right (forms.xhtml, splash.xhtml)
 *
 * Revision 1.9  2005/10/02 21:29:59  tobega
 * Fixed a lot of concurrency (and other) issues from incremental rendering. Also some house-cleaning.
 *
 * Revision 1.8  2005/09/26 22:40:20  tobega
 * Applied patch from Peter Brant concerning margin collapsing
 *
 * Revision 1.7  2005/01/29 20:24:23  pdoubleya
 * Clean/reformat code. Removed commented blocks, checked copyright.
 *
 * Revision 1.6  2004/12/16 15:53:10  joshy
 * fixes for absolute layout
 *
 * Issue number:
 * Obtained from:
 * Submitted by:
 * Reviewed by:
 *
 * Revision 1.5  2004/12/11 23:36:49  tobega
 * Progressing on cleaning up layout and boxes. Still broken, won't even compile at the moment. Working hard to fix it, though.
 *
 * Revision 1.4  2004/12/01 01:57:02  joshy
 * more updates for float support.
 *
 * Issue number:
 * Obtained from:
 * Submitted by:
 * Reviewed by:
 *
 * Revision 1.3  2004/11/18 16:45:12  joshy
 * improved the float code a bit.
 * now floats are automatically forced to be blocks
 *
 *
 * Issue number:
 * Obtained from:
 * Submitted by:
 * Reviewed by:
 *
 * Revision 1.2  2004/10/23 13:50:26  pdoubleya
 * Re-formatted using JavaStyle tool.
 * Cleaned imports to resolve wildcards except for common packages (java.io, java.util, etc).
 * Added CVS log comments at bottom.
 *
 *
 */

