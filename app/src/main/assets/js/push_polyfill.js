(function () {
  if (!("serviceWorker" in navigator)) {
    return;
  }

  var ANDROID_ENDPOINT = "https://android.idleworlds.wrapper/push";
  var subscription = null;

  function buildSubscription() {
    return {
      endpoint: ANDROID_ENDPOINT,
      expirationTime: null,
      getKey: function () {
        return new Uint8Array(65);
      },
      toJSON: function () {
        return {
          endpoint: ANDROID_ENDPOINT,
          expirationTime: null,
          keys: {
            p256dh: "BEl62iUYgUih04f69bA1AkKr8H3lB3xGfF3wGfF3wGfF3wGfF3wGfF3wGfF3wGfF3wGfF3wGfF3wGfF3wGfF3",
            auth: "abcdefghijklmnopqrst"
          }
        };
      },
      unsubscribe: function () {
        subscription = null;
        if (window.Android && window.Android.disablePushPolling) {
          window.Android.disablePushPolling();
        }
        return Promise.resolve(true);
      }
    };
  }

  function attachPushManager(registration) {
    if ("pushManager" in registration) {
      return registration;
    }

    Object.defineProperty(registration, "pushManager", {
      configurable: true,
      value: {
        getSubscription: function () {
          return Promise.resolve(subscription);
        },
        subscribe: function () {
          subscription = buildSubscription();
          if (window.Android && window.Android.enablePushPolling) {
            window.Android.enablePushPolling();
          }
          return Promise.resolve(subscription);
        }
      }
    });

    return registration;
  }

  var originalRegister = navigator.serviceWorker.register.bind(navigator.serviceWorker);
  navigator.serviceWorker.register = function () {
    return originalRegister.apply(this, arguments).then(function (registration) {
      return attachPushManager(registration);
    });
  };

  navigator.serviceWorker.getRegistration().then(function (registration) {
    if (registration) {
      attachPushManager(registration);
    }
  }).catch(function () {});

  if (window.Android && window.Android.reportGameSection) {
    var path = window.location.pathname || "/";
    var section = path.split("/").filter(Boolean)[0] || "task";
    window.Android.reportGameSection(section);
  }

  var originalFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    return originalFetch(input, init).then(function (response) {
      var url = typeof input === "string" ? input : (input && input.url) || "";
      if (url.indexOf("/api/notifications/test") !== -1 && response.ok && window.Android) {
        window.Android.showNotification("IdleWorlds", "Test notification delivered.");
      }
      return response;
    });
  };
})();
