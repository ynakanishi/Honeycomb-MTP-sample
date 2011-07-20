package com.brightechno.android.samples.honeycomb;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImageFragment extends Fragment {
    private static final int MAX_IMAGE_WIDTH = 800;
    private static final int MAX_IMAGE_HEIGHT = 600;
    
    private ImageView mImageView;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Activity activity = getActivity();
        Intent intent = activity.getIntent();
        
        View returnView = inflater.inflate(R.layout.image_fragment, container, false);
        
        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        /*
         * do nothing if UsbDevice instance isn't in the intent.
         */
        if (device != null) {
            /*
             * acquire UsbDeviceConnection instance
             */
            UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);

            /*
             * create MtpDevice instance and open it
             */
            MtpDevice mtpDevice = new MtpDevice(device);
            if (!mtpDevice.open(usbDeviceConnection)) {
                return returnView;
            }
            
            mImageView = (ImageView) returnView.findViewById(R.id.mtp_image);

            SlideShowImageTask task = new SlideShowImageTask(mImageView);

            task.execute(mtpDevice);
        }
        
        return inflater.inflate(R.layout.image_fragment, container, false);
    }
    
    @Override
    public void onPause() {
        // set null drawable for avoiding memory leak
        if (mImageView != null) {
            mImageView.setImageDrawable(null);
        }
        super.onPause();
    }

    private static class SlideShowImageTask extends AsyncTask<MtpDevice, Bitmap, Integer> {
        private ImageView mImageView;
        
        public SlideShowImageTask(ImageView imageView) {
            mImageView = imageView;
        }
        
        @Override
        protected Integer doInBackground(MtpDevice... args) {
            MtpDevice mtpDevice = args[0];
            /*
             * acquire storage IDs in the MTP device
             */
            int[] storageIds = mtpDevice.getStorageIds();
            if (storageIds == null) {
                return null;
            }

            /*
             * scan each storage
             */
            for (int storageId : storageIds) {
                scanObjectsInStorage(mtpDevice, storageId, 0, 0);
            }

            /* close MTP device */
            mtpDevice.close();
            
            return null;
        }

        private void scanObjectsInStorage(MtpDevice mtpDevice, int storageId, int format, int parent) {
            int[] objectHandles = mtpDevice.getObjectHandles(storageId, format, parent);
            if (objectHandles == null) {
                return;
            }
            
            for (int objectHandle : objectHandles) {
                /*
                 *　It's an abnormal case that you can't acquire MtpObjectInfo from MTP device
                 */
                MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(objectHandle);
                if (mtpObjectInfo == null) {
                    continue;
                }
                
                /*
                 * Skip the object if parent doesn't match
                 */
                int parentOfObject = mtpObjectInfo.getParent();
                if (parentOfObject != parent) {
                    continue;
                }
                
                int associationType = mtpObjectInfo.getAssociationType();

                if (associationType == MtpConstants.ASSOCIATION_TYPE_GENERIC_FOLDER) {
                    /* Scan the child folder */
                    scanObjectsInStorage(mtpDevice, storageId, format, objectHandle);
                } else if (mtpObjectInfo.getFormat() == MtpConstants.FORMAT_EXIF_JPEG &&
                        mtpObjectInfo.getProtectionStatus() != MtpConstants.PROTECTION_STATUS_NON_TRANSFERABLE_DATA) {
                    /*
                     *  get bitmap data from the object
                     */
                    byte[] rawObject = mtpDevice.getObject(objectHandle, mtpObjectInfo.getCompressedSize());
                    Bitmap bitmap = null;
                    if (rawObject != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        int scaleW = (mtpObjectInfo.getImagePixWidth() - 1) / MAX_IMAGE_WIDTH + 1;
                        int scaleH = (mtpObjectInfo.getImagePixHeight() - 1) / MAX_IMAGE_HEIGHT  + 1;
                        int scale = Math.max(scaleW, scaleH);
                        if (scale > 0) {
                            options.inSampleSize = scale;
                            bitmap = BitmapFactory.decodeByteArray(rawObject, 0, rawObject.length, options);
                        }
                    }
                    if (bitmap != null) {
                        /* show the bitmap in UI thread */
                        publishProgress(bitmap);
                    }
                }
            }
        }
        
        @Override
        protected void onProgressUpdate(Bitmap... values) {
            Bitmap bitmap = values[0];

            mImageView.setImageBitmap(bitmap);
            
            super.onProgressUpdate(values);
        }
    }
}

