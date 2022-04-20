import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author LING 1158149596@qq.com
 * @date 2022/4/20 16:09
 */
public class HBaseTest {
    private static final String TABLE_NAME = "hbase_test";
    private static final String ANCHOR = "anchor";
    private static final String CONTENTS = "contents";

    @Test
    public void createTable(){
        List<String> columnFamilies = Arrays.asList(ANCHOR, CONTENTS);
        boolean result = HBaseUtil.createTable(TABLE_NAME, columnFamilies);
        System.out.println(result);
    }

    @Test
    public void insertData() {
        //同一个rowkey,同一个列簇，同一个列，此时CNN2和CNN是两个不同时刻的值，获取cnnsi.com时,默认是CNN2，也就是最新值
        List<Pair<String, String>> value1 = Arrays.asList(Pair.of("cnnsi.com", "CNN"),Pair.of("my.look.ca", "CNN.com"));
        List<Pair<String, String>> value2 = Arrays.asList(Pair.of("cnnsi.com", "CNN2"),Pair.of("my.look.ca", "CNN.com2"));
        HBaseUtil.putRow(TABLE_NAME, "rowKey1", ANCHOR, value1);
        HBaseUtil.putRow(TABLE_NAME, "rowKey1", ANCHOR, value2);

        List<Pair<String, String>> value3 = Arrays.asList(Pair.of("html", "xxxxxxxxx"));
        HBaseUtil.putRow(TABLE_NAME, "rowKey2", CONTENTS, value3);
    }

    @Test
    public void getRow() {
        Result result = HBaseUtil.getRow(TABLE_NAME, "rowKey1");
        if (result != null) {
            System.out.println(Bytes.toString(result.getValue(Bytes.toBytes(ANCHOR), Bytes.toBytes("cnnsi.com"))));
            System.out.println(Bytes.toString(result.getValue(Bytes.toBytes(ANCHOR), Bytes.toBytes("my.look.ca"))));
        }
    }

    @Test
    public void getCell() {
        String cell = HBaseUtil.getCell(TABLE_NAME, "rowKey1", ANCHOR, "cnnsi.com");
        System.out.println(cell);

        //获取所有版本的值
        List<String> allVersionCell = HBaseUtil.getAllVersionCell(TABLE_NAME, "rowKey1", ANCHOR, "cnnsi.com");
        System.out.println(allVersionCell);
    }
}
