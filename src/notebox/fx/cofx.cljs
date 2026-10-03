(ns notebox.fx.cofx
  "Coeffects that keep event handlers pure:

    (rf/inject-cofx :notebox/now)        ; :now, an ISO timestamp string
    (rf/inject-cofx :notebox/new-slugs)  ; :new-slugs, 3 fresh nano-id slugs
    (rf/inject-cofx :notebox/uuid)       ; :uuid, a random UUID string

  Tests configure `now-fn` / `slug-fn` for predictable values."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.domain.note :as note]
            [notebox.domain.time :as time]))

(defmethod ig/init-key :notebox.fx/cofx [_ {:keys [now-fn slug-fn]}]
  (let [now-fn  (or now-fn #(js/Date.now))
        slug-fn (or slug-fn note/new-slug)]
    (rf/reg-cofx :notebox/now #(assoc % :now (time/iso-string (now-fn))))
    (rf/reg-cofx :notebox/new-slugs #(assoc % :new-slugs (vec (repeatedly 3 slug-fn))))
    (rf/reg-cofx :notebox/uuid #(assoc % :uuid (str (random-uuid))))
    {}))
