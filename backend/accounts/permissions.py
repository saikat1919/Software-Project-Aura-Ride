from rest_framework.permissions import BasePermission

from accounts.models import UserStatus


class IsFullyVerified(BasePermission):
    """
    Gates sensitive actions (ride request/accept). Rejects the limited 'pending' JWT
    issued at registration (eng review Issue 1) and anyone not ACTIVE.
    """
    message = "Account not verified for this action."

    def has_permission(self, request, view):
        token = request.auth
        scope = token.get("scope", "full") if token is not None else "full"
        if scope == "pending":
            return False
        return bool(request.user and request.user.is_authenticated
                    and request.user.status == UserStatus.ACTIVE)
