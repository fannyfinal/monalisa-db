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
package com.tsc9526.monalisa.core.query.validator;

import java.util.ArrayList;
import java.util.List;

import com.tsc9526.monalisa.core.annotation.Column;
import com.tsc9526.monalisa.core.query.model.Model;
import com.tsc9526.monalisa.core.tools.ClassHelper;
import com.tsc9526.monalisa.core.tools.ClassHelper.FGS;

/**
 * 
 * @author zzg.zhou(11039850@qq.com)
 */
public class Validator {
	/**
	 * 校验字段数据的是否合法.
	 * 
	 * @param m model to validate
	 * @return 不合法的字段列表{字段名: 错误信息}. 如果没有错误, 则为空列表.
	 */
	public List<String> validate(Model<?> m){
		List<String> result=new ArrayList<String>();
		
		for(FGS fgs:m.fields()){
			Column c=fgs.getAnnotation(Column.class);
			Object v=fgs.getObject(this);
			
			if(c.notnull() && "NULL".equals(c.value()) && v==null){
				result.add(fgs.getFieldName()+": CAN NOT BE NULL");
			}else{			
				if(v!=null){
					Regex regex=fgs.getAnnotation(Regex.class);
					if(regex!=null){
						if(v.toString().matches(regex.value())==false){
							String msg=regex.message();
							if(msg==null || msg.length()==0){
								msg="NOT MATCH REGEX: "+regex.value();
							}
							result.add(fgs.getFieldName()+": "+msg);
						}
					}
					
					Max max=fgs.getAnnotation(Max.class);
					if(max!=null){
						Long x=(Long)ClassHelper.convert(v,Long.class);
						if(x > max.value()){
							String msg=max.message();
							if(msg==null || msg.length()==0){
								msg="CAN NOT > "+max.value();
							}
							result.add(fgs.getFieldName()+": "+msg);						
						}
					}
					
					Min min=fgs.getAnnotation(Min.class);
					if(min!=null){
						Long x=(Long)ClassHelper.convert(v,Long.class);
						if(x < min.value()){
							String msg=min.message();
							if(msg==null || msg.length()==0){
								msg="CAN NOT < "+min.value();
							}
							result.add(fgs.getFieldName()+": "+msg);						
						}
					}
				}
			}
		}
		
		return result;
	}

}
