package com.sdtech.rombackup.common;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.sdtech.rombackup.R;

public class ProgressDialog extends AlertDialog {
    private View dView;
    private TextView dMessage;
    public ProgressDialog(Context context){
        super(context);
        dView=LayoutInflater.from(context).inflate(R.layout.progress_dialog,null,false);
        dMessage=dView.findViewById(R.id.message_text);
        setView(dView);
        setCancelable(false);
    }
    @Override
    public void setMessage(CharSequence message) {
        dMessage.setText(String.format("%s, please waits...",message));
    }
    
}
