(ns notebox.fx.cofx
  "Coeffects that keep event handlers pure:

    (rf/inject-cofx :notebox/now)        ; :now, an ISO timestamp string
    (rf/inject-cofx :notebox/new-slugs)  ; :new-slugs, 3 fresh nano-id slugs
    (rf/inject-cofx :notebox/uuid)       ; :uuid, a random UUID string

  Tests pass `now-fn` / `slug-fn` for predictable values. Plain functions;
  notebox.shell.effects registers them."
  (:require [notebox.domain.note :as note]
            [notebox.domain.time :as time]))

(defn coeffects
  "{cofx-id handler}."
  [{:keys [now-fn slug-fn]}]
  (let [now-fn  (or now-fn #(js/Date.now))
        slug-fn (or slug-fn note/new-slug)]
    {:notebox/now       #(assoc % :now (time/iso-string (now-fn)))
     :notebox/new-slugs #(assoc % :new-slugs (vec (repeatedly 3 slug-fn)))
     :notebox/uuid      #(assoc % :uuid (str (random-uuid)))}))
