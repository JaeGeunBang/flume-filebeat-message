package com.wz.flume;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.FlumeException;
import org.apache.flume.conf.ComponentConfiguration;
import org.apache.flume.sink.hbase.HbaseEventSerializer;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.util.Bytes;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import twitter4j.internal.org.json.JSONObject;

public class JsonHbaseMessageSerializer implements HbaseEventSerializer {

    // Config vars
    public static final String COL_NAME_CONFIG = "colNames";

    public static final String CHARSET_CONFIG = "charset";
    public static final String CHARSET_DEFAULT = "UTF-8";

    protected byte[] cf;
    private byte[] payload;
    private List<byte[]> colNames = Lists.newArrayList();
    private Charset charset;
    private int rowKeyIndex;

    public void configure(Context context) {
        charset = Charset.forName(context.getString(CHARSET_CONFIG, CHARSET_DEFAULT));

        String colNameStr = context.getString(COL_NAME_CONFIG);
        String[] columnNames = colNameStr.split(",");
        for (String s : columnNames) {
            colNames.add(s.getBytes(charset));
        }
    }

    public void configure(ComponentConfiguration conf) {
    }

    public void initialize(Event event, byte[] columnFamily) {
        this.payload = event.getBody();
        this.cf = columnFamily;
    }

    public List<Row> getActions() throws FlumeException {
        List<Row> actions = Lists.newArrayList();
        JSONObject inputJson ;
        String payloadData = new String(payload, charset);

        try {
            inputJson = new JSONObject(payloadData);
        } catch (Exception e) {
            // logger.debug("payload is not proper json");
            return Lists.newArrayList();
        }

        if (inputJson.length() == 0 || inputJson.length() != colNames.size()) {
            return Lists.newArrayList();
        }

        try {
            // extract message value
            String m_value = inputJson.get("message").toString();
            String[] m_value_list = m_value.split("\t") ;

            rowKeyIndex = m_value_list.length;
            byte[] colName = colNames.get(rowKeyIndex + 1) ;
            Put put = new Put(colName);

            for (int i = 0; i < m_value_list.length; i++) {
                if (i != rowKeyIndex) {
                    colName = colNames.get(i);
                    put.add(cf, colName, inputJson.get(Bytes.toString(colName)).toString().getBytes(Charsets.UTF_8));
                }
            }
            actions.add(put);

        } catch (Exception e) {
            throw new FlumeException("Could not get row key!", e);
        }
        return actions;
    }

    public List<Increment> getIncrements() {
        return Lists.newArrayList();
    }

    public void close() {
    }
}
