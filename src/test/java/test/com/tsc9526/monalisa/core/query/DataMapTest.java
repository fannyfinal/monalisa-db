/*******************************************************************************************
 *	Copyright (c) 2016, zzg.zhou(11039850@qq.com)
 * 
 *  Monalisa is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.

 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.

 *	You should have received a copy of the GNU Lesser General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************************/
package test.com.tsc9526.monalisa.core.query;

import java.text.SimpleDateFormat;

import junit.framework.Assert;

import org.testng.annotations.Test;

import com.tsc9526.monalisa.core.query.DataMap;

/**
 * 
 * @author zzg.zhou(11039850@qq.com)
 */
@Test
public class DataMapTest {

	public void testIndex()throws Exception {
		SimpleDateFormat yyyyMMddHHmmss=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat yyyyMMddHHmm  =new SimpleDateFormat("yyyy-MM-dd HH:mm");
		SimpleDateFormat yyyyMMddHH    =new SimpleDateFormat("yyyy-MM-dd HH");
		SimpleDateFormat yyyyMMdd      =new SimpleDateFormat("yyyy-MM-dd");
		
		String d1="2006-03-05 15:15:15";
		String d2="2006-03-05 15:15";
		String d3="2006-03-05 15";
		String d4="2006-03-05";
	 
		
		DataMap m=new DataMap();
		m.put("a", "a");
		m.put("B", "b");
		m.put("d1",d1);
		m.put("d2",d2);
		m.put("d3",d3);
		m.put("d4",d4);
		
		Assert.assertEquals("a",m.get(0));
		Assert.assertEquals("b",m.get("b"));
		
		Assert.assertEquals(yyyyMMddHHmmss.parse(d1).getTime(),m.getDate("d1").getTime());
		Assert.assertEquals(yyyyMMddHHmm.parse(d2).getTime(),m.getDate("d2").getTime());
		Assert.assertEquals(yyyyMMddHH.parse(d3).getTime(),m.getDate("d3").getTime());
		Assert.assertEquals(yyyyMMdd.parse(d4).getTime(),m.getDate("d4").getTime());
	}

}
