from keras.models import Sequential, Model
from keras.layers import Reshape, Activation, Conv2D, Input, MaxPooling2D, BatchNormalization, Flatten, Dense, Lambda
from keras.layers.advanced_activations import LeakyReLU
from keras.callbacks import EarlyStopping, ModelCheckpoint, TensorBoard
from keras.optimizers import SGD, Adam, RMSprop
from keras.layers.merge import concatenate
import matplotlib.pyplot as plt
import keras.backend as K
import tensorflow as tf
from tqdm import tqdm
import numpy as np
import pickle
import os, cv2
from utils import WeightReader, decode_netout, draw_boxes
from object_detection_model import YOLO2MODEL

class ObjectDetector:
    def __init__(self, weights_path):        
        self.wt_path = weights_path
        self.yoloModelObj = YOLO2MODEL()
        self.model = self.yoloModelObj.build()
        self.load_model_weights()
    
    def load_model_weights(self):
        weight_reader = WeightReader(self.wt_path)
        #weight_reader.reset()
        nb_conv = 23

        for i in range(1, nb_conv+1):
            conv_layer = self.model.get_layer('conv_' + str(i))

            if i < nb_conv:
                norm_layer = self.model.get_layer('norm_' + str(i))

                size = np.prod(norm_layer.get_weights()[0].shape)

                beta  = weight_reader.read_bytes(size)
                gamma = weight_reader.read_bytes(size)
                mean  = weight_reader.read_bytes(size)
                var   = weight_reader.read_bytes(size)

                weights = norm_layer.set_weights([gamma, beta, mean, var])       

            if len(conv_layer.get_weights()) > 1:
                bias   = weight_reader.read_bytes(np.prod(conv_layer.get_weights()[1].shape))
                kernel = weight_reader.read_bytes(np.prod(conv_layer.get_weights()[0].shape))
                kernel = kernel.reshape(list(reversed(conv_layer.get_weights()[0].shape)))
                kernel = kernel.transpose([2,3,1,0])
                conv_layer.set_weights([kernel, bias])
            else:
                kernel = weight_reader.read_bytes(np.prod(conv_layer.get_weights()[0].shape))
                kernel = kernel.reshape(list(reversed(conv_layer.get_weights()[0].shape)))
                kernel = kernel.transpose([2,3,1,0])
                conv_layer.set_weights([kernel])
                
        return
    
    def detect_obj(self, image):
        dummy_array = np.zeros((1,1,1,1,self.yoloModelObj.TRUE_BOX_BUFFER,4))
        input_image = cv2.resize(image, (416, 416))
        input_image = input_image / 255.
        input_image = input_image[:,:,::-1]
        input_image = np.expand_dims(input_image, 0)

        netout = self.model.predict([input_image, dummy_array])

        boxes = decode_netout(netout[0], 
                              obj_threshold=self.yoloModelObj.OBJ_THRESHOLD,
                              nms_threshold=self.yoloModelObj.NMS_THRESHOLD,
                              anchors=self.yoloModelObj.ANCHORS, 
                              nb_class=self.yoloModelObj.CLASSES)

        image = draw_boxes(image, boxes, labels=self.yoloModelObj.LABELS)
        
        return image
    