package com.lrenyi.template.platform.support;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.meta.FieldMeta;

/**
 * 将实体列表按 {@link EntityMeta} 中可导出字段（未标 {@link com.lrenyi.template.platform.annotation.ExportExclude}）导出为 Excel（.xlsx）。
 * 通过反射使用 Apache POI，避免编译期强依赖，运行时需 classpath 存在 poi-ooxml。
 */
public final class ExcelExportSupport {
    
    private static final String SHEET_NAME = "data";
    
    private ExcelExportSupport() {
    }
    
    /**
     * 生成 Excel 字节数组。第一行为表头（字段名），后续为数据行；仅包含未标记 exportExcluded 的字段。
     * 运行时需存在 poi-ooxml，否则抛出异常。
     */
    public static byte[] toExcel(EntityMeta meta, List<?> data, ObjectMapper objectMapper) throws Exception {
        List<FieldMeta> exportFields = meta.getFields().stream().filter(f -> !f.isExportExcluded()).toList();
        PoiReflect reflect = PoiReflect.create();
        if (exportFields.isEmpty()) {
            Object wb = reflect.newWorkbook();
            try {
                Object sheet = reflect.createSheet(wb, SHEET_NAME);
                Object headerRow = reflect.createRow(sheet, 0);
                reflect.createCellSetValue(headerRow, 0, "(无导出字段)");
                return reflect.writeToBytes(wb);
            } finally {
                reflect.close(wb);
            }
        }
        List<String> columnNames = exportFields.stream().map(FieldMeta::getName).toList();
        Map<String, ExportValueConverter> converterCache = new HashMap<>();
        Object wb = reflect.newWorkbook();
        try {
            Object sheet = reflect.createSheet(wb, SHEET_NAME);
            int rowNum = 0;
            Object headerRow = reflect.createRow(sheet, rowNum++);
            for (int i = 0; i < columnNames.size(); i++) {
                reflect.createCellSetValue(headerRow, i, columnNames.get(i));
            }
            for (Object entity : data) {
                @SuppressWarnings("unchecked") Map<String, Object> map = objectMapper.convertValue(entity, Map.class);
                Object row = reflect.createRow(sheet, rowNum++);
                for (int i = 0; i < columnNames.size(); i++) {
                    Object value = map.get(columnNames.get(i));
                    FieldMeta field = exportFields.get(i);
                    Object exportValue = applyConverter(field, value, converterCache);
                    reflect.setCellValue(row, i, exportValue);
                }
            }
            return reflect.writeToBytes(wb);
        } finally {
            reflect.close(wb);
        }
    }
    
    private static Object applyConverter(FieldMeta field, Object value, Map<String, ExportValueConverter> cache) {
        String className = field.getExportConverterClassName();
        if (className == null || className.isEmpty()) {
            return value;
        }
        ExportValueConverter converter = cache.get(className);
        if (converter == null) {
            try {
                Class<?> clazz = Class.forName(className);
                converter = (ExportValueConverter) clazz.getDeclaredConstructor().newInstance();
                cache.put(className, converter);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate export converter: " + className, e);
            }
        }
        return converter.toExportValue(value);
    }
    
    private static final class PoiReflect {
        private final Class<?> workbookClass;
        private final Method createSheet;
        private final Method createRow;
        private final Method createCell;
        private final Method setCellValueStr;
        private final Method setCellValueNum;
        private final Method setCellValueBool;
        private final Method setBlank;
        private final Method write;
        
        static PoiReflect create() throws Exception {
            Class<?> wbClass = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            Class<?> sheetClass = Class.forName("org.apache.poi.ss.usermodel.Sheet");
            Class<?> rowClass = Class.forName("org.apache.poi.ss.usermodel.Row");
            Class<?> cellClass = Class.forName("org.apache.poi.ss.usermodel.Cell");
            return new PoiReflect(wbClass, sheetClass, rowClass, cellClass);
        }
        
        PoiReflect(Class<?> workbookClass,
                Class<?> sheetClass,
                Class<?> rowClass,
                Class<?> cellClass) throws Exception {
            this.workbookClass = workbookClass;
            this.createSheet = workbookClass.getMethod("createSheet", String.class);
            this.createRow = sheetClass.getMethod("createRow", int.class);
            this.createCell = rowClass.getMethod("createCell", int.class);
            this.setCellValueStr = cellClass.getMethod("setCellValue", String.class);
            this.setCellValueNum = cellClass.getMethod("setCellValue", double.class);
            this.setCellValueBool = cellClass.getMethod("setCellValue", boolean.class);
            this.setBlank = cellClass.getMethod("setBlank");
            this.write = workbookClass.getMethod("write", java.io.OutputStream.class);
        }
        
        Object newWorkbook() throws Exception {
            return workbookClass.getDeclaredConstructor().newInstance();
        }
        
        Object createSheet(Object workbook, String name) throws Exception {
            return createSheet.invoke(workbook, name);
        }
        
        Object createRow(Object sheet, int rowNum) throws Exception {
            return createRow.invoke(sheet, rowNum);
        }
        
        void createCellSetValue(Object row, int colIndex, String value) throws Exception {
            Object cell = createCell.invoke(row, colIndex);
            setCellValueStr.invoke(cell, value != null ? value : "");
        }
        
        void setCellValue(Object row, int colIndex, Object value) throws Exception {
            Object cell = createCell.invoke(row, colIndex);
            switch (value) {
                case null -> {
                    setBlank.invoke(cell);
                    return;
                }
                case Number n -> setCellValueNum.invoke(cell, n.doubleValue());
                case Boolean b -> setCellValueBool.invoke(cell, b);
                default -> setCellValueStr.invoke(cell, value.toString());
            }
        }
        
        byte[] writeToBytes(Object workbook) throws Exception {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                write.invoke(workbook, out);
                return out.toByteArray();
            }
        }
        
        void close(Object workbook) throws Exception {
            if (workbook instanceof AutoCloseable ac) {
                ac.close();
            }
        }
    }
}
