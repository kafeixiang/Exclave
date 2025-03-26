package io.nekohasekai.sagernet.fmt.matsuri;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import cn.hutool.json.JSONObject;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.ktx.Logs;
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager;
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager.Protocol;
import org.jetbrains.annotations.NotNull;

public class MatsuriBean extends AbstractBean {

    public static final Creator<MatsuriBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public MatsuriBean newInstance() {
            return new MatsuriBean();
        }

        @Override
        public MatsuriBean[] newArray(int size) {
            return new MatsuriBean[size];
        }
    };

    public JSONObject allConfig = null;
    public String plgId;
    public String protocolId;
    public JSONObject sharedStorage = new JSONObject();

    @NotNull
    public static JSONObject tryParseJSON(String input) {
        JSONObject ret;
        try {
            ret = new JSONObject(input);
        } catch (Exception e) {
            ret = new JSONObject();
            Logs.INSTANCE.e(e.toString());
        }
        return ret;
    }

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocolId == null) protocolId = "";
        if (plgId == null) plgId = "moe.matsuri.plugin.donotexist";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(plgId);
        output.writeString(protocolId);
        output.writeString(sharedStorage.toString());
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        plgId = input.readString();
        protocolId = input.readString();
        sharedStorage = tryParseJSON(input.readString());
    }

    public String displayType() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return "Unknown";
        return p.getProtocolId();
    }

    @Override
    public boolean canMapping() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().getBool("canMapping");
    }

    @Override
    public boolean canICMPing() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().getBool("canICMPing");
    }

    @Override
    public boolean canTCPing() {
        Protocol p = MatsuriPluginManager.INSTANCE.findProtocol(protocolId);
        if (p == null) return false;
        return p.getProtocolConfig().getBool("canTCPing");
    }

    @NotNull
    @Override
    public MatsuriBean clone() {
        return KryoConverters.deserialize(new MatsuriBean(), KryoConverters.serialize(this));
    }
}
