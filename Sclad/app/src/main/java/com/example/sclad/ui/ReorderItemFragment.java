package com.example.sclad.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.sclad.R;
import com.example.sclad.Utils.*;
import com.example.sclad.models.Device;
import com.example.sclad.models.DeviceType;
import com.example.sclad.models.EnumHelper;
import com.example.sclad.models.RestockOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ReorderItemFragment extends Fragment {

    private EditText productNameInput;
    private Spinner categoryDropdown;
    private SeekBar quantitySeekBar;
    private Switch sendNotificationSwitch;
    private TextView quantityTextView;
    private Button submitBtn;
    private TextView selectedProductQuantity;

    private static ObjectMapper objectMapper = new ObjectMapper();

    public ReorderItemFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reorder_item, container, false);
        Spinner categoryDropdown = view.findViewById(R.id.categoryDropdown);

        List<String> categories = EnumHelper.getDevicesTitleList();

        // I could not find a better way to set a default value to spinner - reason for this is to simulate Accept test 6
        // Ok then
        categories.add(0, "-");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.support_simple_spinner_dropdown_item, categories);
        categoryDropdown.setAdapter(adapter);

        this.productNameInput = view.findViewById(R.id.productNameInput);
        this.categoryDropdown = view.findViewById(R.id.categoryDropdown);
        this.quantitySeekBar = view.findViewById(R.id.quantitySeekBar);
        this.sendNotificationSwitch = view.findViewById(R.id.sendNotificationSwitch);
        this.submitBtn = view.findViewById(R.id.submitBtn);
        this.quantityTextView = view.findViewById(R.id.quantityTextView);
        this.quantityTextView.setText(Integer.toString(this.quantitySeekBar.getProgress()));
        this.selectedProductQuantity = view.findViewById(R.id.selectedProductQuantity);

        productNameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!productNameInput.getText().toString().isEmpty()) {
                String productName = productNameInput.getText().toString();
                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(new BasicAuthInterceptor(SecurityContextHolder.username,
                                SecurityContextHolder.password)).build();
                Request request = new Request.Builder()
                        .url(UrlHelper.resolveApiEndpoint("/api/device/getDeviceByProductName/" + productName)).build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        selectedProductQuantity.setText("Seems like device " + productNameInput.getText().toString() + " does not exist.");
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Response httpResponse = response;
                        ResponseBody body = response.body();
                        if (httpResponse.code() == 200 && body != null) {
                            Device device = objectMapper.readValue(body.string(), Device.class);
                            String returnString = "Product quantity: " + device.getQuantity();
                            if (device.getReordered()) {
                                returnString = returnString + " | Is reordered. ";
                            } else {
                                returnString = returnString + " | Is not reordered. ";
                            }
                            String finalReturnString = returnString;
                            getActivity().runOnUiThread(() -> selectedProductQuantity.setText(finalReturnString));
                        }
                    }
                });
            }
        });
        quantitySeekBar.setOnSeekBarChangeListener(onChangeListener);
        submitBtn.setOnClickListener(onClickListener);
        return view;
    }

    public View.OnClickListener onClickListener = event -> {
        //Get input from fields in the form
        String productName = productNameInput.getText().toString();
        Boolean sendNotification = sendNotificationSwitch.isChecked();
        Integer quantity = quantitySeekBar.getProgress();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BasicAuthInterceptor(SecurityContextHolder.username,
                        SecurityContextHolder.password)).build();
        ObjectMapper mapper = new ObjectMapper();
        Request request = new Request.Builder()
                .url(UrlHelper.resolveApiEndpoint("/api/device/getDeviceByProductName/" + productName)).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //nothing
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    if (responseBody != null && response.code() == 200) {
                        String category = categoryDropdown.getSelectedItem().toString();
                        Device device = mapper.readValue(responseBody, Device.class);
                        if (!category.equals(device.getDeviceType())) {
                            getActivity().runOnUiThread(() -> ToastDisplayHelper
                                    .displayShortToastMessage("Device with the selected category does not exist!.", getActivity()));
                            return;
                        }

                        if (device.getReordered()) {
                            getActivity().runOnUiThread(() -> ToastDisplayHelper
                                    .displayShortToastMessage("The selected device has been already reordered!.", getActivity()));
                            return;
                        }
                        RestockOrder restockOrder = new RestockOrder();
                        restockOrder.setDevice(device);
                        restockOrder.setProductName(device.getProductName());
                        restockOrder.setQuantityToReorder(quantity);
                        restockOrder.setSendNotification(sendNotification);
                        restockOrder.setDeviceType(DeviceType.valueOf(device.getDeviceType()));
                        JSONObject restockOrderJson = JsonHelper.toJson(restockOrder, device);
                        RequestBody body = RequestBody.create(MediaType.parse("application/json"), String.valueOf(restockOrderJson));
                        Request postRequest = new Request.Builder().post(body).url(UrlHelper.resolveApiEndpoint("/api/restockOrder/create")).build();
                        client.newCall(postRequest).execute();
                        getActivity().runOnUiThread(() -> {
                            ToastDisplayHelper.displayShortToastMessage("Created restock order for device " + device.getProductName(), getActivity());
                            startActivity(new Intent(getActivity(),
                                    DashBoardActivity.class).putExtra("USERNAME", SecurityContextHolder.username));
                        });
                    } else {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast t = Toast.makeText(getContext(), "Unknown device.", Toast.LENGTH_SHORT);
                                t.show();
                            }
                        });

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    };

    public android.widget.SeekBar.OnSeekBarChangeListener onChangeListener = new SeekBar.OnSeekBarChangeListener() {
        int value = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            value = progress;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            //not needed
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            quantityTextView = Objects.requireNonNull(getView()).findViewById(R.id.quantityTextView);
            quantityTextView.setText(Integer.toString(value));
        }
    };
}
