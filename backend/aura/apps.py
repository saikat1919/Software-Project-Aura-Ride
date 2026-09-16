"""Make AuraAdminSite the default admin site, so @admin.register and /admin/ both use it
(the documented `default_site` hook — no re-registering models needed)."""
from django.contrib.admin.apps import AdminConfig


class AuraAdminConfig(AdminConfig):
    default_site = "aura.admin.AuraAdminSite"
