package com.xiaohongshu.demo4copy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private ScrollView mScrollView;
    private ExpandableLayout mExpandableLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mExpandableLayout = (ExpandableLayout) findViewById(R.id.expandableLayout);
        mExpandableLayout.setExpandeBodyScollEnabledCallback(new ExpandableLayout.IExpandeBodyScollEnabledCallback() {
            @Override
            public boolean canBodyScroll(boolean isUp2Down) {
                return isUp2Down && mScrollView.getScrollY() > 0;
            }
        });
    }

    public void textClick(View view) {
        Toast.makeText(this, "text click", Toast.LENGTH_SHORT).show();
    }
}
