from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path


def root(_request):
    return JsonResponse({
        "service": "Aura Ride API",
        "admin": "/admin/",
        "api_base": "/api/v1/",
        "endpoints": ["/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/me"],
    })


urlpatterns = [
    path("", root),
    path("admin/", admin.site.urls),
    path("api/v1/", include("accounts.urls")),
    path("api/v1/", include("rides.urls")),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
