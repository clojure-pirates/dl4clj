(ns ^{:doc "Default gradient implementation. Basically lookup table for ndarrays
see: https://deeplearning4j.org/doc/org/deeplearning4j/nn/gradient/DefaultGradient.html"}
    dl4clj.nn.gradient.default-gradient
  (:import [org.deeplearning4j.nn.gradient DefaultGradient]))

(defn new-default-gradient
  [& {:keys [flattened-gradient]
      :as opts}]
  (if (contains? opts :flattened-gradient)
    (DefaultGradient. flattened-gradient)
    (DefaultGradient.)))

(defn clear!
  "Clear residual parameters (useful for returning a gradient and then clearing old objects)"
  [& {:keys [grad]}]
  (doto grad (.clear)))

(defn flattening-order-for-variables
  "Return the gradient flattening order for the specified variable, or null if it is not explicitly set"
  [& {:keys [grad variable]}]
  (.flatteningOrderForVariable grad variable))

(defn get-gradient-for
  "The gradient for the given variable"
  [& {:keys [grad variable]}]
  (.getGradientFor grad variable))

(defn gradient
  "The full gradient as one flat vector"
  [& {:keys [grad order]}]
  (if order
    (.gradient grad order)
    (.gradient grad)))

(defn gradient-for-variable
  "Gradient look up table"
  [& {:keys [grad]}]
  (.gradientForVariable grad))

(defn set-gradient-for!
  "Update gradient for the given variable; also (optionally) specify the order in which the array should be flattened to a row vector"
  [& {:keys [grad variable new-gradient flattening-order]}]
  (if flattening-order
    (doto grad (.setGradientFor variable new-gradient flattening-order))
    (doto grad (.setGradientFor variable new-gradient))))
