(ns dynamo.override-test
  (:require [clojure.test :refer :all]
            [dynamo.graph :as g]
            [support.test-support :refer :all]
            [internal.util :refer :all])
  (:import  [javax.vecmath Vector3d]))

(g/defnode MainNode
  (property a-property g/Str)
  (property b-property g/Str)
  (property virt-property g/Str
            (value (g/fnk [a-property] a-property)))
  (input sub-nodes g/NodeID :array :cascade-delete)
  (output sub-nodes [g/NodeID] (g/fnk [sub-nodes] sub-nodes))
  (output cached-output g/Str :cached (g/fnk [a-property] a-property))
  (input cached-values g/Str :array)
  (output cached-values [g/Str] :cached (g/fnk [cached-values] cached-values)))

(g/defnode SubNode
  (property value g/Str))

(defn- override [node-id]
  (-> (g/override node-id {})
    :tx-data
    tx-nodes))

(defn- setup
  ([world]
    (setup world 0))
  ([world count]
    (let [nodes (tx-nodes (g/make-nodes world [main [MainNode :a-property "main" :b-property "main"]
                                               sub SubNode]
                                        (g/connect sub :_node-id main :sub-nodes)))]
      (loop [result [nodes]
             counter count]
        (if (> counter 0)
          (recur (conj result (override (first (last result)))) (dec counter))
          result)))))

(deftest test-sanity
  (with-clean-system
    (let [[[main sub]] (setup world)]
      (testing "Original graph connections"
               (is (= [sub] (g/node-value main :sub-nodes)))))))

(deftest default-behaviour
   (with-clean-system
     (let [[[main sub] [or-main or-sub]] (setup world 1)]
       (testing "Connection is overridden from start"
               (is (= [or-sub] (g/node-value or-main :sub-nodes)))
               (is (= or-main (g/node-value or-main :_node-id))))
       (testing "Using base values"
                (doseq [label [:a-property :b-property :virt-property :cached-output]]
                  (is (= "main" (g/node-value or-main label)))))
       (testing "Base node invalidates cache"
                (g/transact (g/set-property main :a-property "new main"))
                (is (= "new main" (g/node-value or-main :cached-output)))))))

(deftest delete
  (with-clean-system
    (let [[[main sub] [or-main or-sub]] (setup world 1)]
      (testing "Overrides can be removed"
               (g/transact (g/delete-node or-main))
               (doseq [nid [or-main or-sub]]
                 (is (nil? (g/node-by-id nid))))
               (is (empty? (g/overrides main)))))
    (let [[[main sub] [or-main or-sub]] (setup world 1)]
      (testing "Overrides are removed with base"
               (g/transact (g/delete-node main))
               (doseq [nid [main sub or-main or-sub]]
                 (is (nil? (g/node-by-id nid))))
               (is (empty? (g/overrides main)))))
    (let [[[main sub] [or-main or-sub] [or2-main or2-sub]] (setup world 2)]
      (testing "Delete hierarchy"
               (g/transact (g/delete-node main))
               (doseq [nid [main sub or-main or-sub or2-main or2-sub]]
                 (is (nil? (g/node-by-id nid))))
               (is (empty? (g/overrides main)))))))

(deftest override-property
  (with-clean-system
    (let [[[main sub] [or-main or-sub]] (setup world 1)]
      (testing "Overriding property"
               (g/transact (g/set-property or-main :a-property "overridden main"))
               (is (= "overridden main" (g/node-value or-main :a-property)))
               (is (= "main" (g/node-value main :a-property)))
               (doseq [label [:_properties :_declared-properties]
                       :let [props (-> (g/node-value or-main label) (get :properties))
                             a-p (:a-property props)
                             b-p (:b-property props)]]
                 (is (= "overridden main" (:value a-p)))
                 (is (= "main" (:original-value a-p)))
                 (is (= or-main (:node-id a-p)))
                 (is (= "main" (:value b-p)))
                 (is (= false (contains? b-p :original-value)))
                 (is (= or-main (:node-id b-p)))))
      (testing "Virtual property"
               (is (= "overridden main" (g/node-value or-main :virt-property))))
      (testing "Output production"
               (is (= "overridden main" (g/node-value or-main :cached-output)))
               (is (= "main" (g/node-value main :cached-output))))
      (testing "Clearing property"
               (g/transact (g/clear-property or-main :a-property))
               (is (= "main" (g/node-value or-main :a-property)))
               (is (= "main" (g/node-value or-main :virt-property)))
               (is (= "main" (g/node-value or-main :cached-output))))
      (testing "Update property"
               (g/transact (g/update-property or-main :a-property (fn [prop] (str prop "_changed"))))
               (is (= "main_changed" (g/node-value or-main :a-property)))))))

(deftest new-node-created
  (with-clean-system
    (let [[main or-main sub] (tx-nodes (g/make-nodes world [main MainNode]
                                                     (:tx-data (g/override main {}))
                                                     (g/make-nodes world [sub SubNode]
                                                                   (g/connect sub :_node-id main :sub-nodes))))]
      (let [sub-nodes (g/node-value or-main :sub-nodes)]
        (is (= 1 (count sub-nodes)))
        (is (not= [sub] sub-nodes)))
      (g/transact (g/disconnect sub :_node-id main :sub-nodes))
      (is (empty? (g/node-value or-main :sub-nodes))))))

(deftest new-node-created-cache-invalidation
  (with-clean-system
    (let [[main or-main] (tx-nodes (g/make-nodes world [main MainNode]
                                                 (:tx-data (g/override main {}))))
          _ (is (= [] (g/node-value or-main :cached-values)))
          [sub] (tx-nodes (g/make-nodes world [sub [SubNode :value "sub"]]
                                        (for [[from to] [[:_node-id :sub-nodes]
                                                         [:value :cached-values]]]
                                          (g/connect sub from main to))))]
      (is (= ["sub"] (g/node-value or-main :cached-values)))
      (g/transact (concat
                    (g/disconnect sub :_node-id main :sub-nodes)
                    (g/disconnect sub :value main :cached-values)))
      (is (= [] (g/node-value or-main :cached-values))))))

(g/defnode DetectCacheInvalidation
  (property invalid-cache g/Any (default (atom 0)))
  (input value g/Str :cascade-delete)
  (output cached-value g/Str :cached (g/fnk [value invalid-cache]
                                            (swap! invalid-cache inc)
                                            value)))

(deftest inherit-targets []
  (testing "missing override"
           (with-clean-system
             (let [[main cache-node or-main] (tx-nodes (g/make-nodes world [main [SubNode :value "test"]
                                                                            cache DetectCacheInvalidation]
                                                                     (g/connect main :value cache :value)
                                                                     (:tx-data (g/override main {}))))]
               (is (= cache-node (ffirst (g/targets-of main :value))))
               (is (empty? (g/targets-of or-main :value))))))
  (testing "existing override"
           (with-clean-system
             (let [[main cache-node] (tx-nodes (g/make-nodes world [main [SubNode :value "test"]
                                                                    cache DetectCacheInvalidation]
                                                             (g/connect main :value cache :value)))
                   [or-cache-node or-main] (tx-nodes (:tx-data (g/override cache-node {})))]
               (is (= cache-node (ffirst (g/targets-of main :value))))
               (is (= or-cache-node (ffirst (g/targets-of or-main :value))))))))

(g/defnode StringInput
  (input value g/Str :cascade-delete))

(deftest inherit-sources []
  (testing "missing override"
           (with-clean-system
             (let [[main cache-node or-main] (tx-nodes (g/make-nodes world [main StringInput
                                                                            cache DetectCacheInvalidation]
                                                                     (g/connect cache :cached-value main :value)
                                                                     (:tx-data (g/override main {}))))]
               (is (= cache-node (ffirst (g/sources-of main :value))))
               (is (= cache-node (ffirst (g/sources-of or-main :value)))))))
  (testing "existing override"
           (with-clean-system
             (let [[main cache-node] (tx-nodes (g/make-nodes world [main StringInput
                                                                    cache DetectCacheInvalidation]
                                                             (g/connect cache :cached-value main :value)))
                   [or-main or-cache-node] (tx-nodes (:tx-data (g/override cache-node {})))]
               (is (= cache-node (ffirst (g/sources-of main :value))))
               (is (= or-cache-node (ffirst (g/sources-of or-main :value))))))))

(deftest lonely-override-leaves-cache []
  (with-clean-system
    (let [[main cache-node or-main] (tx-nodes (g/make-nodes world [main [SubNode :value "test"]
                                                                   cache DetectCacheInvalidation]
                                                            (g/connect main :value cache :value)
                                                            (:tx-data (g/override main {}))))]
      (is (= 0 @(g/node-value cache-node :invalid-cache)))
      (is (= "test" (g/node-value cache-node :cached-value)))
      (is (= 1 @(g/node-value cache-node :invalid-cache)))
      (is (= "test" (g/node-value cache-node :cached-value)))
      (is (= 1 @(g/node-value cache-node :invalid-cache)))
      (g/transact (g/set-property or-main :value "override"))
      (is (= 1 @(g/node-value cache-node :invalid-cache))))))

(deftest multi-override
  (with-clean-system
    (let [[[main sub]
           [or-main or-sub]
           [or2-main or2-sub]] (setup world 2)]
      (testing "basic default behaviour"
               (is (= "main" (g/node-value or2-main :a-property))))
      (testing "cache-invalidation in multiple overrides"
               (g/transact (g/set-property main :a-property "new main"))
               (is (= "new main" (g/node-value or2-main :a-property))))
      (testing "override one below overrides"
               (g/transact (g/set-property or-main :a-property "main2"))
               (is (= "main2" (g/node-value or2-main :a-property))))
      (testing "top level override"
               (g/transact (g/set-property or2-main :a-property "main3"))
               (is (= "main3" (g/node-value or2-main :a-property)))))))

(deftest mark-defective
  (with-clean-system
    (let [[[main sub]
           [or-main or-sub]] (setup world 1)]
      (testing "defective base"
               (let [error (g/error-severe {:type :some-error :message "Something went wrong"})]
                 (g/transact
                   (g/mark-defective main error))
                 (is (g/error? (g/node-value or-main :a-property))))))
    (let [[[main sub]
           [or-main or-sub]] (setup world 1)]
      (testing "defective override, which throws exception"
               (let [error (g/error-severe {:type :some-error :message "Something went wrong"})]
                 (is (thrown? Exception (g/transact
                                          (g/mark-defective or-main error)))))))))

(deftest copy-paste
  (with-clean-system
    (let [[[main sub]
           [or-main or-sub]] (setup world 1)]
      (g/transact (g/set-property or-main :a-property "override"))
      (let [fragment (g/copy [or-main] {:traverse? (constantly true)})
            paste-data (g/paste (g/node-id->graph-id or-main) fragment {})
            copy-id (first (:root-node-ids paste-data))]
        (g/transact (:tx-data paste-data))
        (is (= "override" (g/node-value copy-id :a-property)))))))

(g/defnode ExternalNode
  (property value g/Keyword))

(g/defnode ResourceNode
  (property reference g/Keyword
            (value (g/fnk [in-reference] in-reference))
            (set (fn [basis node-id old-value new-value]
                   (concat
                     (g/disconnect-sources basis node-id :in-reference)
                     (let [gid (g/node-id->graph-id node-id)
                           node-store (g/graph-value gid :node-store)
                           src-id (get node-store new-value)]
                       (if src-id
                         (g/connect src-id :value node-id :in-reference)
                         []))))))
  (input in-reference g/Keyword))

(deftest connection-property
  (with-clean-system
    (let [[res a b] (tx-nodes (g/make-nodes world [res ResourceNode
                                                  a [ExternalNode :value :node-a]
                                                  b [ExternalNode :value :node-b]]
                                           (g/set-graph-value world :node-store {:node-a a :node-b b})))]
      (testing "linking through property setter"
               (g/transact (g/set-property res :reference :node-a))
               (is (= :node-a (g/node-value res :reference)))
               (g/transact (g/set-property a :value :node-a2))
               (is (= :node-a2 (g/node-value res :reference))))
      (let [or-res (first (override res))]
        (testing "override inherits the connection"
                 (is (= :node-a2 (g/node-value or-res :reference))))
        (testing "override the actual connection"
                 (g/transact (g/set-property or-res :reference :node-b))
                 (is (= :node-b (g/node-value or-res :reference))))
        (testing "clearing the property"
                 (g/transact (g/clear-property or-res :reference))
                 (is (= :node-a2 (g/node-value or-res :reference))))))))

(def ^:private IDMap {g/Str g/NodeID})

(g/defnode Node
  (property id g/Str)
  (input id-prefix g/Str)
  (output id g/Str (g/fnk [id id-prefix] (str id-prefix id)))
  (output node-ids IDMap (g/fnk [_node-id id] {id _node-id})))

(g/defnode VisualNode
  (inherits Node)
  (property value g/Str))

(g/defnode Scene
  (property path g/Str)
  (input nodes g/NodeID :array :cascade-delete)
  (input node-ids IDMap :array)
  (input node-properties g/Properties :array)
  (input id-prefix g/Str)
  (output id-prefix g/Str (g/fnk [id-prefix] id-prefix))
  (output node-ids IDMap (g/fnk [node-ids] (reduce into {} node-ids)))
  (output node-overrides g/Any :cached (g/fnk [node-ids node-properties]
                                              (let [node-ids (clojure.set/map-invert node-ids)
                                                    properties (group-by (comp node-ids :node-id second) (filter (comp :original-value second) (mapcat :properties node-properties)))]
                                                (into {} (map (fn [[nid props]] [nid (into {} (map (fn [[key value]] [key (:value value)]) props))]) properties))))))

(defn- scene-by-path
  ([graph path]
    (scene-by-path (g/now) graph path))
  ([basis graph path]
    (let [resources (or (g/graph-value basis graph :resources) {})]
      (get resources path))))

(g/defnode Template
  (inherits Node)
  (property path g/Any
            (set (fn [basis self old-value new-value]
                   (concat
                     (if-let [instance (g/node-value self :instance :basis basis)]
                       (g/delete-node instance)
                       [])
                     (let [gid (g/node-id->graph-id self)
                           path (:path new-value)]
                       (if-let [scene (scene-by-path basis gid path)]
                         (let [{:keys [id-mapping tx-data]} (g/override scene {})
                               path (g/node-value self :template-path :basis basis)
                               mapping (comp id-mapping (into {} (map (fn [[k v]] [(str path k) v])
                                                                      (g/node-value scene :node-ids :basis basis))))
                               set-prop-data (for [[id props] (:overrides new-value)
                                                   :let [node-id (mapping id)]
                                                   [key value] props]
                                               (g/set-property node-id key value))
                               or-scene (id-mapping scene)]
                           (concat
                             tx-data
                             set-prop-data
                             (for [[from to] [[:node-ids :node-ids]
                                              [:node-overrides :node-overrides]]]
                               (g/connect or-scene from self to))
                             (g/connect self :template-path or-scene :id-prefix)))
                         []))))))

  (input instance g/NodeID)
  (input scene-path g/Str)
  (input node-ids IDMap :cascade-delete)
  (input node-overrides g/Any)
  (output template-path g/Str (g/fnk [id] (str id "/")))
  (output node-ids IDMap (g/fnk [_node-id id node-ids] (into {id _node-id} node-ids))))

(def ^:private scene-inputs [[:_node-id :nodes]
                             [:node-ids :node-ids]
                             [:_properties :node-properties]])

(def ^:private scene-outputs [[:id-prefix :id-prefix]])

(defn- make-scene! [graph path nodes]
  (let [resources (or (g/graph-value graph :resources) {})]
    (tx-nodes (g/make-nodes graph [scene [Scene :path path]]
                            (-> (g/set-graph-value graph :resources (assoc resources path scene))
                              ((partial reduce (fn [tx [node-type props]]
                                                 (into tx (g/make-nodes graph [n [node-type props]]
                                                                        (for [[output input] scene-inputs]
                                                                          (g/connect n output scene input))
                                                                        (for [[output input] scene-outputs]
                                                                          (g/connect scene output n input))))))
                                nodes))))))

(deftest scene-loading
  (with-clean-system
    (let [[scene node] (make-scene! world "my-scene" [[VisualNode {:id "my-node" :value "initial"}]])
          overrides {"my-template/my-node" {:value "new value"}}
          [super-scene template] (make-scene! world "my-super-scene" [[Template {:id "my-template" :path {:path "my-scene" :overrides overrides}}]])]
      (is (= "initial" (g/node-value node :value)))
      (let [or-node (get (g/node-value template :node-ids) "my-template/my-node")]
        (is (= "new value" (g/node-value or-node :value))))
      (is (= overrides (g/node-value template :node-overrides))))))

(defn- has-node? [scene node-id]
  (contains? (g/node-value scene :node-ids) node-id))

(defn- node-by-id [scene node-id]
  (get (g/node-value scene :node-ids) node-id))

(defn- target [n label]
  (ffirst (g/targets-of n label)))

(deftest hierarchical-ids
  (with-clean-system
    (let [[sub-scene] (make-scene! world "sub-scene" [[VisualNode {:id "my-node" :value ""}]])
          [scene] (make-scene! world "scene" [[Template {:id "template" :path {:path "sub-scene" :overrides {}}}]])
          [super-scene] (make-scene! world "super-scene" [[Template {:id "super-template" :path {:path "scene" :overrides {}}}]])]
      (is (has-node? sub-scene "my-node"))
      (is (has-node? scene "template/my-node"))
      (is (has-node? super-scene "super-template/template/my-node")))))

;; User-defined dynamic properties

(g/defnode Script
  (property script-properties g/Any)
  (output _properties g/Properties :cached (g/fnk [_node-id _declared-properties script-properties]
                                                  (-> _declared-properties
                                                    (update :properties dissoc :script-properties)
                                                    (update :properties merge (into {} (map (fn [[key value]] [key {:value value
                                                                                                                    :type (g/make-property-type key (type value))
                                                                                                                    :node-id _node-id}]) script-properties)))
                                                    (update :display-order (comp vec (partial remove #{:script-properties})))))))

(g/defnode Component
  (property id g/Str)
  (property component g/Any
            (set (fn [basis self old-value new-value]
                   (concat
                     (if-let [instance (g/node-value self :instance :basis basis)]
                       (g/delete-node instance)
                       [])
                     (let [gid (g/node-id->graph-id self)
                           path (:path new-value)]
                       (if-let [script (get (g/graph-value basis gid :resources) path)]
                         (let [{:keys [id-mapping tx-data]} (g/override basis script {})
                               or-script (id-mapping script)
                               script-props (g/node-value script :_properties :basis basis)
                               set-prop-data (for [[key value] (:overrides new-value)]
                                               (g/set-property or-script key value))
                               conn-data (for [[src tgt] [[:_node-id :instance]
                                                          [:_properties :script-properties]]]
                                           (g/connect or-script src self tgt))]
                           (concat tx-data set-prop-data conn-data))
                         []))))))
  (input instance g/NodeID)
  (input script-properties g/Properties)
  (output _properties g/Properties (g/fnk [_declared-properties script-properties]
                                          (-> _declared-properties
                                            (update :properties merge (:properties script-properties))
                                            (update :display-order concat (:display-order script-properties))))))

(deftest custom-properties
  (with-clean-system
    (let [[script comp] (tx-nodes (g/make-nodes world [script [Script :script-properties {:speed 0}]]
                                               (g/set-graph-value world :resources {"script.script" script}))
                                  (g/make-nodes world [comp [Component :component {:path "script.script" :overrides {:speed 10}}]]))]
      (let [p (get-in (g/node-value comp :_properties) [:properties :speed])]
        (is (= 10 (:value p)))
        (is (= 0 (:original-value p)))
        (g/transact (g/set-property comp :speed 20))
        (let [p' (get-in (g/node-value comp :_properties) [:properties :speed])]
          (is (= 20 (:value p')))
          (is (= 0 (:original-value p'))))
        (g/transact (g/clear-property comp :speed))
        (let [p' (get-in (g/node-value comp :_properties) [:properties :speed])]
          (is (= 0 (:value p')))
          (is (not (contains? p' :original-value))))))))

;; Overloaded outputs with different types

(g/defnode TypedOutputNode
  (property value [(g/one g/Num "x") (g/one g/Num "y") (g/one g/Num "z")])
  (output value Vector3d (g/fnk [value] (let [[x y z] value] (Vector3d. x y z))))
  (output complex {g/Keyword Vector3d} (g/fnk [value] {:value value})))

(deftest overloaded-outputs-and-types
  (with-clean-system
    (let [[a b] (tx-nodes (g/make-nodes world [n [TypedOutputNode :value [1 2 3]]]
                                        (:tx-data (g/override n))))]
      (g/transact (g/set-property b :value [2 3 4]))
      (is (not= (g/node-value b :complex) (g/node-value a :complex))))))

;; Dynamic property production

(g/defnode IDNode
  (input super-id g/Str)
  (property id g/Str (value (g/fnk [_node-id super-id id] (if super-id (str super-id "/" id) id)))))

(deftest dynamic-id-in-properties
  (with-clean-system
    (let [[node parent sub] (tx-nodes (g/make-nodes world [node [IDNode :id "child-id"]
                                                           parent [IDNode :id "parent-id"]]
                                                    (let [or-data (g/override node)
                                                          or-node (get-in or-data [:id-mapping node])]
                                                      (concat
                                                        (:tx-data or-data)
                                                        (g/connect parent :id or-node :super-id)))))]
      (is (= (g/node-value sub :id)
             (get-in (g/node-value sub :_declared-properties) [:properties :id :value]))))))
