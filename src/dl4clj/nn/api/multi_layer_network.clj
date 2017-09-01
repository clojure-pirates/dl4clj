(ns dl4clj.nn.api.multi-layer-network
  (:import [org.deeplearning4j.nn.multilayer MultiLayerNetwork]
           [org.deeplearning4j.nn.api Layer])
  (:require [dl4clj.utils :refer [contains-many? array-of]]
            [dl4clj.helpers :refer [new-lazy-iter reset-if-empty?! reset-iterator!]]
            [clojure.core.match :refer [match]]
            [dl4clj.constants :as enum]
            [nd4clj.linalg.factory.nd4j :refer [vec-or-matrix->indarray]]))

(defn initialize!
  "Sets the input and labels from this dataset

  :ds (dataset), a dataset
   -see: nd4clj.linalg.dataset.data-set"
  [& {:keys [mln ds]}]
  (doto mln
    (.initialize ds)))

(defn evaluate-classification
  "if you only supply mln and iter: Evaluate the network (classification performance)
  if you supply mln, iter and labels-list: Evaluate the network on the provided data set.
  if you supply all args: Evaluate the network (for classification) on the provided data set,
                          with top N accuracy in addition to standard accuracy.

  :iter (ds-iter), a dataset iterator
   - see: dl4clj.datasets.iterators

  :labels (coll), a collection of strings (the labels)

  :top-n (int), N value for top N accuracy evaluation"
  [& {:keys [mln iter labels top-n]
      :as opts}]
  (let [ds-iter (reset-iterator! iter)]
    (match [opts]
           [{:mln _ :ds-iter _ :labels-list _ :top-n _}]
           (.evaluate mln ds-iter (into '() labels) top-n)
           [{:mln _ :ds-iter _ :labels-list _}]
           (.evaluate mln ds-iter (into '() labels))
           :else
           (.evaluate mln ds-iter))))

(defn evaluate-regression
  "Evaluate the network for regression performance

  :iter (ds-iter), a dataset iterator
   - see: dl4clj.datasets.iterators"
  [& {:keys [mln iter]}]
  (.evaluateRegression mln (reset-iterator! iter)))

(defn evaluate-roc
  "Evaluate the network (must be a binary classifier) on the specified data
   - see:dl4clj.eval.roc.rocs

  :iter (ds-iter), a dataset iterator
   - see: dl4clj.datasets.iterators

  :roc-threshold-steps (int), value needed to call the ROC constructor
   - see: dl4clj.eval.roc.rocs"
  [& {:keys [mln iter roc-threshold-steps]}]
  (.evaluateROC mln (reset-iterator! iter) roc-threshold-steps))

(defn evaluate-roc-multi-class
  "Evaluate the network on the specified data.

  :iter (ds-iter), a dataset iterator
   - see: dl4clj.datasets.iterators

  :roc-threshold-steps (int), value needed to call the ROCMultiClass constructor
   - see: dl4clj.eval.roc.rocs"
  [& {:keys [mln iter roc-threshold-steps]}]
  (.evaluateROCMultiClass mln (reset-iterator! iter) roc-threshold-steps))

(defn score-examples
  "Calculate the score for each example in a DataSet individually.
   - this fn allows for examples to be scored individually (at test time only),
     which may be useful for example for autoencoder architectures and the like.

  :dataset (datatset),
   -see: nd4clj.linalg.dataset.data-set
         dl4clj.datasets.datavec

  :add-regularization-terms? (boolean), if true, add l1/l2 terms to the score
   otherwise just return the scores

  :iter (ds-iter), dataset iterator
   - see: dl4clj.datasets.iterators"
  [& {:keys [mln dataset add-regularization-terms? iter]
      :as opts}]
  (match [opts]
         [{:mln _ :dataset _ :add-regularization-terms? _}]
         (.scoreExamples mln dataset add-regularization-terms?)
         [{:mln _ :iter _ :add-regularization-terms? _}]
         (.scoreExamples mln (reset-iterator! iter) add-regularization-terms?)
         :else (assert false "you must supply data in the form of a dataset or a dataset iterator.
you must also supply whether or not you want to add regularization terms (L1, L2, dropout...)")))

(defn output
  "label the probabilities of the input or if masks are supplied,
  calculate the output of the network with masking arrays

  :iter (ds-iter), a dataset iterator
   - see: dl4clj.datasets.iterators

  :train? (boolean), are we in training mode?
   - This mainly affect hyper parameters such as drop out
     where certain things should be applied with activations

  :input (INDArray or vec), the input to label

  :features-mask (INDArray or vec), the mask used for the features

  :labels-mask (INDArray or vec), the mask used for the labels

  :training-mode (keyword), another way to say if its training or testing mode

  NOTE: this fn only resets the iterator if its empty"
  [& {:keys [iter train? input features-mask labels-mask
             training-mode mln]
      :as opts}]
  (let [ds-iter (if iter
                  (reset-if-empty?! iter))
        i (if input
            (vec-or-matrix->indarray input))]
    (match [opts]
           [{:mln _ :input _ :train? _ :features-mask _ :labels-mask _}]
           (.output mln i train?
                    (vec-or-matrix->indarray features-mask)
                    (vec-or-matrix->indarray labels-mask))
           [{:mln _ :input _ :training-mode _}]
           (.output mln i (enum/value-of {:layer-training-mode training-mode}))
           [{:mln _ :input _ :train? _}]
           (.output mln i train?)
           [{:mln _ :iter _ :train? _}]
           (.output mln ds-iter train?)
           [{:mln _ :input _}]
           (.output mln i)
           [{:mln _ :iter _}]
           (.output mln ds-iter)
           :else
           (assert false "you must supply atleast an input or iterator"))))


(defn initialize-layers!
  "initialize the neuralNets based on the input.

  :input (INDArray or vec), the input matrix for training"
  [& {:keys [mln input]}]
  (doto mln
    (.initializeLayers (vec-or-matrix->indarray input))))

(defn pre-train!
  "Perform layerwise pretraining on all pre-trainable layers in the network (VAEs, RBMs, Autoencoders, etc)
  Note that pretraining will be performed on one layer after the other, resetting the DataSetIterator between iterations.
  For multiple epochs per layer, appropriately wrap the iterator (for example, a MultipleEpochsIterator)
  or train each layer manually using (pre-train-layer! layer-idx DataSetIterator)

  :iter (ds-iter), dataset iterator
   - see: dl4clj.datasets.iterators"
  [& {:keys [mln iter]}]
  (.pretrain mln (reset-iterator! iter)))

(defn pre-train-layer!
  "Perform layerwise unsupervised training on a single pre-trainable layer
  in the network (VAEs, RBMs, Autoencoders, etc) If the specified layer index
  (0 to n-layers - 1) is not a pretrainable layer, this is a no-op.

  :layer-idx (int), the index of the layer you want to pretrain

  :iter (ds-iter), dataset iterator
   - see: dl4clj.datasets.iterators

  :features (INDArray or vec), training data array"
  [& {:keys [mln layer-idx iter features]
      :as opts}]
  (match [opts]
         [{:mln _ :layer-idx _ :iter _}]
         (.pretrainLayer mln layer-idx (reset-iterator! iter))
         [{:mln _ :layer-idx _ :features _}]
         (.pretrainLayer mln layer-idx (vec-or-matrix->indarray features))
         :else (assert false "you must supply the layer's index and either a dataset
 iterator or an array of features to pretrain on")))

(defn fine-tune!
  "Run SGD based on the given labels

  returns the fine tuned model"
  [mln]
  (doto mln
    (.finetune)))

(defn rnn-time-step
  "If this MultiLayerNetwork contains one or more RNN layers:
  conduct forward pass (prediction) but using previous stored state for any RNN layers.
   -  The activations for the final step are also stored in the RNN layers for
      use next time this fn is called.

  :input (INDArray or vec), Input to network. May be for one or multiple time steps.
   - For single time step: input has shape [miniBatchSize,inputSize] or [miniBatchSize,inputSize,1].
       - miniBatchSize=1 for single example.
   - For multiple time steps: [miniBatchSize,inputSize,inputTimeSeriesLength]"
  [& {:keys [mln input]}]
  (.rnnTimeStep mln (vec-or-matrix->indarray input)))

(defn reconstruct
  "reconstructs the input from the output of a given layer

  :layer-output (INDArray or vec), the input to transform

  :layer-idx (int), the layer to output for encoding

  returns a reconstructed matrix relative to the size of the last hidden layer
   - normally a probability distribution summing to one"
  [& {:keys [mln layer-output layer-idx]
      :as opts}]
  (assert (contains-many? opts :layer-output :layer-idx) "you must supply a layer and the input")
  (.reconstruct mln (vec-or-matrix->indarray layer-output) layer-idx))

(defn summary
  "String detailing the architecture of the multi-layer-network. (mln)"
  [mln]
  (.summary mln))

(defn activate-selected-layers
  "Calculate activation for few layers at once. Suitable for autoencoder partial activation

  returns the activation from the last layer

   :from (int), starting layer idx

   :to (int), ending layer idx

   :input (INDArray or vec), the input to propagate through the layers"
  [& {:keys [mln from to input]}]
  (.activateSelectedLayers mln from to (vec-or-matrix->indarray input)))

(defn activate-from-prev-layer
  "Calculate activation from previous layer including pre processing where necessary

  :current-layer-idx (int), the index of the current layer
   - you will get the activation from the layer directly before this one

  :input (INDArray or vec), the input to propagate through the layers

  :training? (boolean), is this training mode?"
  [& {:keys [mln current-layer-idx input training?]}]
  (.activationFromPrevLayer mln current-layer-idx
                            (vec-or-matrix->indarray input) training?))

(defn clear-layer-mask-arrays!
  "Remove the mask arrays from all layers.

  returns the multi layer network after the mutation"
  [mln]
  (doto mln
    (.clearLayerMaskArrays)))

(defn compute-z
  "if you only supply training?: Compute input linear transformation (z) of the output layer
  if you supply training? and input: Compute activations from input to output of the output layer
   - both ways return the list of activations for each layer

  :training? (boolean), training mode?

  :input (INDArray or vec), the input to propagate through the network for calcing activations"
  [& {:keys [mln training? input]
      :as opts}]
  (if input
    (.computeZ mln (vec-or-matrix->indarray input) training?)
    (.computeZ mln training?)))

(defn get-epsilon
  "returns epsilon for a given multi-layer-network (mln)"
  [mln]
  (.epsilon mln))

(defn feed-forward
  "if :features-mask and :labels-mask supplied:

   Compute the activations from the input to the output layer,
   given mask arrays (that may be null) The masking arrays are used in situations
   such an one-to-many and many-to-one rucerrent neural network (RNN) designs,
   as well as for supporting time series of varying lengths within the same minibatch for RNNs.

  else, just compute the activations from the input to the output layer

  :train? (boolean), is this training mode?

  :input (INDArray or vec), the input to be propagated through the network

  :features-mask (INDArray or vec), mask for the input features

  :labels-mask (INDArray or vec), mask for the labels"
  [& {:keys [mln train? input features-mask labels-mask]
      :as opts}]
  (let [i (vec-or-matrix->indarray input)]
    (match [opts]
           [{:mln _ :input _ :features-mask _ :labels-mask _}]
           (.feedForward mln i
                         (vec-or-matrix->indarray features-mask)
                         (vec-or-matrix->indarray labels-mask))
           [{:mln _ :input _ :train? _}]
           (.feedForward mln i train?)
           [{:mln _ :input _}]
           (.feedForward mln i)
           [{:mln _ :train? _}]
           (.feedForward mln train?)
           :else
           (.feedForward mln))))

(defn feed-forward-to-layer
  "Compute the activations from the input to the specified layer.
   - if input is not supplied, uses the currently set input for the mln

  :layer-idx (int), the index of the layer you want the input propagated through

  :train? (boolean), are we in training mode?

  :input (INDArray or vec), the input to propagate through the specified layer

  Note: the returned output list contains the original input at idx 0"
  [& {:keys [mln layer-idx train? input]
      :as opts}]
  (let [i (vec-or-matrix->indarray input)]
    (match [opts]
           [{:mln _ :layer-idx _ :train? _ :input _}]
           (.feedForwardToLayer mln layer-idx i train?)
           [{:mln _ :layer-idx _ :input _}]
           (.feedForwardToLayer mln layer-idx i)
           [{:mln _ :layer-idx _ :train? _}]
           (.feedForwardToLayer mln layer-idx train?)
           :else
           (assert false "you must supply a mln, a layer-idx and either/both train? and input"))))

(defn get-default-config
  "gets the default config for the multi-layer-network"
  [mln]
  (.getDefaultConfiguration mln))

(defn get-input
  "return the input to the mln"
  [mln]
  (.getInput mln))

(defn get-layer
  "return the layer of the mln based on its position within the mln

  :layer-idx (int), the index of the layer you want to get from the mln

  :layer-name (str), the name of the layer you want to get from the mln"
  [& {:keys [mln layer-idx layer-name]
      :as opts}]
  (match [opts]
         [{:mln _ :layer-idx _}]
         (.getLayer mln layer-idx)
         [{:mln _ :layer-name _}]
         (.getLayer mln layer-name)
         :else
         (assert false "you must supply a mln and either the layer's name or index")))

(defn get-layer-names
  "return a list of the layer names in the mln"
  [mln]
  (.getLayerNames mln))

(defn get-layers
  "returns an array of the layers within the mln"
  [mln]
  (.getLayers mln))

(defn get-layer-wise-config
  "returns the configuration for the layers in the mln"
  [mln]
  (.getLayerWiseConfigurations mln))

(defn get-mask
  "return the mask array used in this mln"
  [mln]
  (.getMask mln))

(defn get-n-layers
  "get the number of layers in the mln"
  [mln]
  (.getnLayers mln))

(defn get-output-layer
  "returns the output layer of the mln"
  [mln]
  (.getOutputLayer mln))

(defn get-updater
  "return the updater used in this mln"
  [mln]
  (.getUpdater mln))

(defn init-gradients-view!
  "initializes the flattened gradients array (used in backprop) and
  sets the appropriate subset in all layers.

  - this gets called behind the scene when using fit!"
  [mln]
  (doto mln
    (.initGradientsView)))

(defn get-mln-input
  "returns the input/feature matrix for the model"
  [mln]
  (.input mln))

(defn is-init-called?
  "was the model initialized"
  [mln]
  (.isInitCalled mln))

(defn print-config
  "Prints the configuration and returns the mln"
  [mln]
  (doto mln
    (.printConfiguration)))

(defn rnn-activate-using-stroed-state
  "returns the activation of the rnn given its most recent state
   - does not modify the RNN layer state, pure fn

  :input (INDArray or vec), the input fed to the rnn

  :training? (boolean), is this training mode?

  :store-last-for-tbptt? (boolean), set to true if used as part of truncated bptt training

  returns the activations for each layer
   - the input is idx 0, followed by the activations"
  [& {:keys [mln input training? store-last-for-tbptt?]
      :as opts}]
  (assert (contains-many? opts :input :training? :store-last-for-tbptt?)
          "you must supply a mln, the input to the model, if this is during training
or evaluation and if we want to store the previous state for truncated backprop")
  (.rnnActivateUsingStoredState mln (vec-or-matrix->indarray input) training? store-last-for-tbptt?))

(defn rnn-clear-prev-state!
  "clear the previous state of the rnn layers if any and return the mln"
  [mln]
  (doto mln
    (.rnnClearPreviousState)))

(defn rnn-get-prev-state
  "get the state of the rnn layer given its index in the mln

  :layer-idx (int), the index of the rnn within the mln"
  [& {:keys [mln layer-idx]}]
  (.rnnGetPreviousState mln layer-idx))

(defn rnn-set-prev-state!
  "Set the state of the RNN layer and return the updated mln

  :layer-idx (int), the index of the rnn within the mln

  :state (map), {str INDArray}, The state to set the specified layer to

  returns the mln"
  [& {:keys [mln layer-idx state]
      :as opts}]
  (assert (contains-many? opts :layer-idx :state)
          "you must supply a layer-index for the layer in question within the mln and
a map of the desired state")
  (doto mln
    (.rnnSetPreviousState layer-idx state)))

(defn set-mln-input!
  "Note that if input isn't nil and the neuralNets are nil,
  this is a way of initializing the neural network, returns the mln

  :input (INDArray or vec), the input to the mln"
  [& {:keys [mln input]}]
  (doto mln
    (.setInput (vec-or-matrix->indarray input))))

(defn set-labels-mln!
  "sets the labels given an array of labels,
  returns the mln.

  :labels (INDArray or vec), the labels to be set"
  [& {:keys [mln labels]}]
  (doto mln
    (.setLabels (vec-or-matrix->indarray labels))))

(defn set-layers!
  "sets the layers of the mln in the order in which they appear in the supplied coll.

  :layers (coll), a collection of layers to add to the mln

  returns the mln"
  [& {:keys [mln layers]}]
  (doto mln
    (.setLayers (array-of :data layers
                          :java-type Layer))))

(defn set-layer-wise-config!
  "sets the configuration for a mln given a multi-layer configuration.
  returns the mln

  :mln (multi layer network), the multi layer network

  :multi-layer-conf (multi layer conf), the configuration for the multi layer network

  NOTE: you should not need this fn.  You can set the multi-layer-conf when creating your mln
   - see: new-multi-layer-network at the top of this ns"
  [& {:keys [mln multi-layer-conf]}]
  (doto mln
    (.setLayerWiseConfigurations multi-layer-conf)))

(defn set-mask!
  "set the mask, returns the mln

  :mask (INDArray or vec), the mask to set for the mln"
  [& {:keys [mln mask]}]
  (doto mln
    (.setMask (vec-or-matrix->indarray mask))))

(defn set-parameters!
  "set the paramters for this model (mln).
   - This is used to manipulate the weights and biases across all neuralNets
     (including the output layer)

  :params (INDArray or vec), a parameter vector equal 1,numParameters

  returns the mln"
  [& {:keys [mln params]}]
  (doto mln
    (.setParameters (vec-or-matrix->indarray params))))

(defn set-score!
  "sets the score,

   :score (double), the score to set

  returns the mln"
  [& {:keys [mln score]}]
  (doto mln
    (.setScore score)))

(defn set-updater!
  "sets the updater for a given mln.

  :updater (ml-updater), the updater to use
   - see: dl4clj.nn.updater.multi-layer-updater

  returns the mln"
  [& {:keys [mln updater]}]
  (doto mln
    (.setUpdater updater)))

(defn update-mln!
  "Assigns the parameters of mln to the ones specified by another mln.
  This is used in loading from input streams, factory methods, etc
   - returns mln

  you can also use update! in the model interface ns"
  [& {:keys [mln other-mln]}]
  (doto mln (.update other-mln)))

(defn update-rnn-state-with-tbptt-state!
  "updates the rnn state to be that of the tbptt state.
  returns the mln."
  [mln]
  (doto mln
    (.updateRnnStateWithTBPTTState)))

(defn z-from-prev-layer
  "Compute input linear transformation (z) from previous layer
  Applies pre processing transformation where necessary

  :current-layer-idx (int), the current layer

  :input (INDArray or vec), the input

  :training? (boolean), are we in training mode?

  returns the activation from the previous layer"
  [& {:keys [mln current-layer-idx input training?]
      :as opts}]
  (assert (contains-many? opts :current-layer-idx :input :training?)
          "you must supply the index of the current layer, an input array and if this is for training or evaluation")
  (.zFromPrevLayer mln current-layer-idx (vec-or-matrix->indarray input) training?))
